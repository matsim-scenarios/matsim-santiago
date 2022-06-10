/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.santiago.run;

import java.util.*;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.log4j.Logger;
import org.jfree.util.Log;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.cadyts.car.CadytsCarModule;
import org.matsim.contrib.cadyts.car.CadytsContext;
import org.matsim.contrib.cadyts.general.CadytsScoring;
import org.matsim.contrib.roadpricing.RoadPricingConfigGroup;
import org.matsim.contrib.roadpricing.RoadPricingModule;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;
import org.matsim.core.population.algorithms.PermissibleModesCalculatorImpl;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl.Builder;
import org.matsim.core.replanning.modules.ReRoute;
import org.matsim.core.replanning.modules.SubtourModeChoice;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.MainModeIdentifierImpl;
import org.matsim.core.router.TripRouter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;

import org.matsim.santiago.utils.SantiagoScenarioConstants;

import static org.matsim.core.config.groups.PlanCalcScoreConfigGroup.*;

/**
 * @author benjamin
 *
 */
public class SantiagoScenarioRunner {
	static final String DOING_MODE_CHOICE = "doingModeChoice";
	static final String MAPPING_ACTIVITIES_TO_CAR_LINKS = "mappingActivitiesToCarLinks";
	static final String USING_CADYTS = "usingCadyts";

	private static final Logger log = Logger.getLogger( SantiagoScenarioRunner.class ) ;

	public static void main(String[] args){

		Config config = prepareConfig( args );
		// note: a large number of config modification is included later.  Maybe change at some point?

		run( config, args );


	}
	static void run( Config config, String[] args ){


		CommandLine cmd = ConfigUtils.getCommandLine( args ) ;

		Scenario scenario = ScenarioUtils.loadScenario( config );

		for( Link link : scenario.getNetwork().getLinks().values() ){
			if ( link.getLength() <=0. ) {
				log.warn( "found link with length=" + link.getLength() + "; linkId=" + link.getId() + "; making longer ..." ) ;
				link.setLength( 1. );
			}
			if ( !( link.getFreespeed() <= Double.MAX_VALUE && link.getFreespeed() >0 ) ) {
				double val = 1000. ;
				log.warn( "found link with speed=" + link.getFreespeed() + "; linkId=" + link.getId() + "; setting to " + val );
				link.setFreespeed( val );
			}
		}

		// mapping agents' activities to links on the road network to avoid being stuck on the transit network
		boolean mapActs2Links = Boolean.parseBoolean( cmd.getOption( MAPPING_ACTIVITIES_TO_CAR_LINKS ).orElse( "false" ) ) ;
		if(mapActs2Links){
			mapActivities2properLinks( scenario );
		}

		CommandLine cmd1 = ConfigUtils.getCommandLine( args ) ;
		Controler controler = new Controler( scenario );

		// adding other network modes than car requires some router; here, the same values as for car are used
		setNetworkModeRouting( controler );

		// adding pt fare
		boolean doModeChoice = Boolean.parseBoolean( cmd1.getOption( DOING_MODE_CHOICE ).orElse( "false" ) ) ;
		controler.getEvents().addHandler(new PTFareHandler( controler, doModeChoice, scenario.getPopulation()) );

		// adding basic strategies for car and non-car users
		setBasicStrategiesForSubpopulations( controler );

		// adding subtour mode choice strategies for car and non-car users
		if(doModeChoice){
			setModeChoiceForSubpopulations( controler );
		}
		RoadPricingConfigGroup rpcg = ConfigUtils.addOrGetModule( scenario.getConfig(), RoadPricingConfigGroup.class );
		if ( rpcg.getTollLinksFile()!=null && !rpcg.getTollLinksFile().equals( "" ) ){
			// found road pricing file, so am switching on road pricing:
			controler.addOverridingModule( new RoadPricingModule() );
		}

		boolean cadyts = Boolean.parseBoolean( cmd1.getOption( USING_CADYTS ).orElse( "false" ) ) ;
		if (cadyts){
			controler.addOverridingModule(new CadytsCarModule() );
			// include cadyts into the plan scoring (this will add the cadyts corrections to the scores)
			controler.setScoringFunctionFactory( new ScoringFunctionFactory() {
				@Inject CadytsContext cadytsContext;
				@Inject ScoringParametersForPerson parameters;
				@Override
				public ScoringFunction createNewScoringFunction( Person person ) {
					final ScoringParameters params = parameters.getScoringParameters(person );

					SumScoringFunction scoringFunctionAccumulator = new SumScoringFunction();
					scoringFunctionAccumulator.addScoringFunction(new CharyparNagelLegScoring(params, controler.getScenario().getNetwork()) );
					scoringFunctionAccumulator.addScoringFunction(new CharyparNagelActivityScoring(params) ) ;
					scoringFunctionAccumulator.addScoringFunction(new CharyparNagelAgentStuckScoring(params) );

					final CadytsScoring<Link> scoringFunction = new CadytsScoring<>(person.getSelectedPlan(), scenario.getConfig(), cadytsContext);
					scoringFunction.setWeightOfCadytsCorrection(30. * scenario.getConfig().planCalcScore().getBrainExpBeta() ) ;
					scoringFunctionAccumulator.addScoringFunction(scoringFunction );

					return scoringFunctionAccumulator;
				}
			} ) ;

		}

		controler.addOverridingModule( new AbstractModule(){
			@Override public void install(){
				this.bind( MainModeIdentifier.class ).to( MainModeIdentifierImpl.class );
			}
		} );
		config.plans().setHandlingOfPlansWithoutRoutingMode( PlansConfigGroup.HandlingOfPlansWithoutRoutingMode.useMainModeIdentifier );

		//Run!
		controler.run();
	}
	static Config prepareConfig( String[] args ){

		Gbl.assertNotNull( args );
		Gbl.assertIf( args.length>0 );
		// yy there used to be an execution path when args where not there; this is currently gone; could be resurrected.  On the
		// other hand, from GUI one does not need it, and from IDE one can run test cases.  kai, nov'19
//			configFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml" ;

		Config config = ConfigUtils.loadConfig( args ) ;

		config.plansCalcRoute().removeModeRoutingParams( TransportMode.ride );

		// one could combine the following into a loop over the activity types, but I want to leave the option to set opening/closing times to some of them.
		// kai, nov'19

		config.planCalcScore().addActivityParams( new ActivityParams("busi0.5H").setTypicalDuration( 1800. ) );
		config.planCalcScore().addActivityParams( new ActivityParams("educ0.5H").setTypicalDuration( 1800. ) );
		config.planCalcScore().addActivityParams( new ActivityParams("heal0.5H").setTypicalDuration( 1800. ) );
		config.planCalcScore().addActivityParams( new ActivityParams("home0.5H").setTypicalDuration( 1800. ) );
		config.planCalcScore().addActivityParams( new ActivityParams("leis0.5H").setTypicalDuration( 1800. ) );
		config.planCalcScore().addActivityParams( new ActivityParams("othe0.5H").setTypicalDuration( 1800. ) );
		config.planCalcScore().addActivityParams( new ActivityParams("shop0.5H").setTypicalDuration( 1800. ) );
		config.planCalcScore().addActivityParams( new ActivityParams("visi0.5H").setTypicalDuration( 1800. ) );
		config.planCalcScore().addActivityParams( new ActivityParams("work0.5H").setTypicalDuration( 1800. ) );

		for ( int ii=1 ; ii<=24 ; ii++ ) {
			config.planCalcScore().addActivityParams( new ActivityParams( "busi" + ii + ".0H").setTypicalDuration( ii*3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "educ" + ii + ".0H").setTypicalDuration( ii*3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "heal" + ii + ".0H").setTypicalDuration( ii*3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "home" + ii + ".0H").setTypicalDuration( ii*3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "leis" + ii + ".0H").setTypicalDuration( ii*3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "othe" + ii + ".0H").setTypicalDuration( ii*3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "shop" + ii + ".0H").setTypicalDuration( ii*3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "visi" + ii + ".0H").setTypicalDuration( ii*3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "work" + ii + ".0H").setTypicalDuration( ii*3600. ) );
		}


		return config;
	}


	static void mapActivities2properLinks( Scenario scenario ) {
		Network subNetwork = getNetworkWithProperLinksOnly(scenario.getNetwork());
		for(Person person : scenario.getPopulation().getPersons().values()){
			for (Plan plan : person.getPlans()) {
				for (PlanElement planElement : plan.getPlanElements()) {
					if (planElement instanceof Activity) {
						Activity act = (Activity) planElement;
						Id<Link> linkId = act.getLinkId();
						if(!(linkId == null)){
							throw new RuntimeException("Link Id " + linkId + " already defined for this activity. Aborting... ");
						} else {
							linkId = NetworkUtils.getNearestLink(subNetwork, act.getCoord()).getId();
							act.setLinkId(linkId);
						}
					}
				}
			}
		}
	}

	private static Network getNetworkWithProperLinksOnly(Network network) {
		Network subNetwork;
		TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network);
		Set<String> modes = new HashSet<String>();
		modes.add(TransportMode.car);
		subNetwork = NetworkUtils.createNetwork();
		filter.filter(subNetwork, modes); //remove non-car links

		for(Node n: new HashSet<Node>(subNetwork.getNodes().values())){
			for(Link l: NetworkUtils.getIncidentLinks(n).values()){
				if(l.getFreespeed() > (16.666666667)){
					subNetwork.removeLink(l.getId()); //remove links with freespeed > 60kmh
				}
			}
			if(n.getInLinks().size() == 0 && n.getOutLinks().size() == 0){
				subNetwork.removeNode(n.getId()); //remove nodes without connection to links
			}
		}
		return subNetwork;
	}

	private static void setNetworkModeRouting(Controler controler) {
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());
				addTravelTimeBinding(SantiagoScenarioConstants.Modes.taxi.toString()).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(SantiagoScenarioConstants.Modes.taxi.toString()).to(carTravelDisutilityFactoryKey());
				addTravelTimeBinding(SantiagoScenarioConstants.Modes.colectivo.toString()).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(SantiagoScenarioConstants.Modes.colectivo.toString()).to(carTravelDisutilityFactoryKey());
				addTravelTimeBinding(SantiagoScenarioConstants.Modes.other.toString()).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(SantiagoScenarioConstants.Modes.other.toString()).to(carTravelDisutilityFactoryKey());
			}
		});
	}

	static void setBasicStrategiesForSubpopulations( MatsimServices controler ) {
		setReroute("carAvail", controler);
		setChangeExp("carAvail", controler);
		setReroute(null, controler);
		setChangeExp(null, controler);
	}

	private static void setChangeExp(String subpopName, MatsimServices controler) {
		StrategySettings changeExpSettings = new StrategySettings();
		changeExpSettings.setStrategyName( DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta );
		changeExpSettings.setSubpopulation(subpopName);
//		changeExpSettings.setWeight(0.85);
		changeExpSettings.setWeight(0.7); //TODO: BE AWARE OF THIS!!!
		controler.getConfig().strategy().addStrategySettings(changeExpSettings);
	}

	private static void setReroute(String subpopName, MatsimServices controler) {
		StrategySettings reRouteSettings = new StrategySettings();
		reRouteSettings.setStrategyName( DefaultPlanStrategiesModule.DefaultStrategy.ReRoute );
		reRouteSettings.setSubpopulation(subpopName);
		reRouteSettings.setWeight(0.15);
		controler.getConfig().strategy().addStrategySettings(reRouteSettings);
	}

	static void setModeChoiceForSubpopulations( final Controler controler ) {
		final String nameMcCarAvail = "SubtourModeChoice_".concat("carAvail");
		StrategySettings modeChoiceCarAvail = new StrategySettings();
		modeChoiceCarAvail.setStrategyName(nameMcCarAvail);
		modeChoiceCarAvail.setSubpopulation("carAvail");
		modeChoiceCarAvail.setWeight(0.15);
		controler.getConfig().strategy().addStrategySettings(modeChoiceCarAvail);

		final String nameMcNonCarAvail = "SubtourModeChoice_".concat("nonCarAvail");
		StrategySettings modeChoiceNonCarAvail = new StrategySettings();
		modeChoiceNonCarAvail.setStrategyName(nameMcNonCarAvail);
		modeChoiceNonCarAvail.setSubpopulation(null);
		modeChoiceNonCarAvail.setWeight(0.15);
		controler.getConfig().strategy().addStrategySettings(modeChoiceNonCarAvail);

		//TODO: somehow, there are agents for which the chaining does not work (e.g. agent 10002001) 
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				Log.info("Adding SubtourModeChoice for agents with a car available...");
				final String[] availableModes1 = {TransportMode.car, TransportMode.walk, TransportMode.pt};
				final String[] chainBasedModes1 = {TransportMode.car};
				addPlanStrategyBinding(nameMcCarAvail).toProvider(new SubtourModeChoiceProvider(availableModes1, chainBasedModes1));
				Log.info("Adding SubtourModeChoice for the rest of the agents...");
				final String[] availableModes2 = {TransportMode.walk, TransportMode.pt};
				final String[] chainBasedModes2 = {};
				addPlanStrategyBinding(nameMcNonCarAvail).toProvider(new SubtourModeChoiceProvider(availableModes2, chainBasedModes2));
			}
		});

	}
	/**
	 * @author benjamin
	 *
	 */
	private static final class SubtourModeChoiceProvider implements javax.inject.Provider<PlanStrategy> {
		@Inject Scenario scenario;
		@Inject Provider<TripRouter> tripRouterProvider;
		String[] availableModes;
		String[] chainBasedModes;

		public SubtourModeChoiceProvider(String[] availableModes, String[] chainBasedModes) {
			super();
			this.availableModes = availableModes;
			this.chainBasedModes = chainBasedModes;
		}

		@Override
		public PlanStrategy get() {
			Log.info("Available modes are " + availableModes);
			Log.info("Chain-based modes are " + chainBasedModes);
			final Builder builder = new Builder(new RandomPlanSelector<Plan, Person>());
//			new SubtourModeChoice(scenario.getConfig().global().getNumberOfThreads(), availableModes,
//					chainBasedModes, false, 0.0, // using value of 0.0 (=default) for backward compatibility
//					tripRouterProvider));
			builder.addStrategyModule(new SubtourModeChoice(scenario.getConfig().global(), scenario.getConfig().subtourModeChoice(),
					new PermissibleModesCalculatorImpl(scenario.getConfig()) ) );
			builder.addStrategyModule(new ReRoute(scenario, tripRouterProvider));
			return builder.build();
		}
	}
}
