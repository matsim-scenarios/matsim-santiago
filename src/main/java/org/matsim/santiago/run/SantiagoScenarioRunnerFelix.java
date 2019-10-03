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

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

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
import org.matsim.contrib.roadpricing.RoadPricingConfigGroup;
import org.matsim.contrib.roadpricing.RoadPricingModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl.Builder;
import org.matsim.core.replanning.modules.ReRoute;
import org.matsim.core.replanning.modules.SubtourModeChoice;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.TripRouter;
import org.matsim.core.scenario.ScenarioUtils;

import org.matsim.santiago.SantiagoScenarioConstants;
import org.matsim.santiago.analysis.modalShareFromEvents.ModalShareFromEvents;
import org.matsim.santiago.colectivos.PostProcessModalShare;
import org.matsim.santiago.colectivos.router.ColectivoModule;

/**
 * @author benjamin
 *
 */
public class SantiagoScenarioRunnerFelix {
		
	
	private static String configFile;
	private static int policy;
	private static int sigma;	
	private static boolean doModeChoice; 
	private static boolean mapActs2Links;
	private static String gantriesFile;

	private static String caseName = "v3";
	private static String inputPath = "C:/Users/Felix/Documents/Bachelor/Santiago de Chile/v3/input/";
	private static double colectivoASC;
	
	public static void main(String args[]){		

		if (args.length > 0){ //ONLY FOR CMD CASES
			
			configFile = args[0]; //COMPLETE PATH TO CONFIG.
			gantriesFile = args[1]; //COMPLETE PATH TO TOLL LINKS FILE
			policy = Integer.parseInt(args[2]) ; //POLICY? - 0: BASE CASE, 1: CORDON.
			sigma = Integer.parseInt(args[3]); //SIGMA. 
			doModeChoice = Boolean.parseBoolean(args[4]); //DOMODECHOICE?
			mapActs2Links = Boolean.parseBoolean(args[5]); //MAPACTS2LINKS?
			colectivoASC = Double.parseDouble(args[6]);
			
		} else {
		
			configFile=inputPath + "config_test.xml" ; 
			gantriesFile = inputPath + "gantries.xml";
			policy=0;    
			sigma=3 ;    
			doModeChoice=true ; 
			mapActs2Links=false;
			colectivoASC = -10.0;
		}	
			
			if(policy == 1){
				//TODO: CHANGE THE TollLinksFile IN THE CONFIG.
			}
			
			Config config = ConfigUtils.loadConfig(configFile);
			Scenario scenario = ScenarioUtils.loadScenario(config);
			config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
			Controler controler = new Controler(scenario);
			
			// adding other network modes than car requires some router; here, the same values as for car are used
			setNetworkModeRouting(controler);
			
			//adding colectivo integration
			controler.addOverridingModule(new ColectivoModule());
			
			// adding pt fare
			controler.getEvents().addHandler(new PTFareHandlerFelix(controler, doModeChoice, scenario.getPopulation(), colectivoASC));
			
			// adding basic strategies for car and non-car users
			setBasicStrategiesForSubpopulations(controler);
			
			// adding subtour mode choice strategies for car and non-car users			
			if(doModeChoice) setModeChoiceForSubpopulations(controler);
			
			// mapping agents' activities to links on the road network to avoid being stuck on the transit network
			if(mapActs2Links) mapActivities2properLinks(scenario);
			
			//Adding the toll links file
			RoadPricingConfigGroup rpcg = ConfigUtils.addOrGetModule(config, RoadPricingConfigGroup.GROUP_NAME, RoadPricingConfigGroup.class);
			rpcg.setTollLinksFile(gantriesFile);
			
			//Adding randomness to the router
			config.plansCalcRoute().setRoutingRandomness(sigma); 
			controler.addOverridingModule(new RoadPricingModule());
	
			//Run!
			controler.run();
			
			String[] outputFolder = {controler.getControlerIO().getOutputPath()+"/"};
			PostProcessModalShare.main(outputFolder);
			
			ModalShareFromEvents msg = new ModalShareFromEvents(outputFolder[0]+"output_events.xml.gz");
			msg.run();
			msg.writeResults(outputFolder[0]+"output_modeShare.txt");
	}
	
	

	
	private static void mapActivities2properLinks(Scenario scenario) {
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
				addTravelTimeBinding(SantiagoScenarioConstants.Modes.other.toString()).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(SantiagoScenarioConstants.Modes.other.toString()).to(carTravelDisutilityFactoryKey());
			}
		});
	}

	private static void setBasicStrategiesForSubpopulations(MatsimServices controler) {
		setReroute("carAvail", controler);
		setChangeExp("carAvail", controler);
		setReroute(null, controler);
		setChangeExp(null, controler);
	}

	private static void setChangeExp(String subpopName, MatsimServices controler) {
		StrategySettings changeExpSettings = new StrategySettings();
		changeExpSettings.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta.toString());
		changeExpSettings.setSubpopulation(subpopName);
		changeExpSettings.setWeight(0.7);
		controler.getConfig().strategy().addStrategySettings(changeExpSettings);
	}

	private static void setReroute(String subpopName, MatsimServices controler) {
		StrategySettings reRouteSettings = new StrategySettings();
		reRouteSettings.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString());
		reRouteSettings.setSubpopulation(subpopName);
		reRouteSettings.setWeight(0.15);
		controler.getConfig().strategy().addStrategySettings(reRouteSettings);
	}

	private static void setModeChoiceForSubpopulations(final Controler controler) {
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
			builder.addStrategyModule(new SubtourModeChoice(scenario.getConfig().global().getNumberOfThreads(), availableModes, chainBasedModes, false, 
					0.0, // using value of 0.0 (=default) for backward compatibility
					tripRouterProvider));
			builder.addStrategyModule(new ReRoute(scenario, tripRouterProvider));
			return builder.build();
		}
	}	
}
