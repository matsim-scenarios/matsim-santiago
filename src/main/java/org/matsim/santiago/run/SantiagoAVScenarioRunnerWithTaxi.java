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

import javax.inject.Inject;
import javax.inject.Provider;

import org.jfree.util.Log;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.contrib.cadyts.car.CadytsCarModule;
import org.matsim.contrib.cadyts.car.CadytsContext;
import org.matsim.contrib.cadyts.general.CadytsScoring;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.otfvis.OTFVisLiveModule;
import org.matsim.contrib.roadpricing.RoadPricingModule;
import org.matsim.contrib.taxi.run.MultiModeTaxiModule;
import org.matsim.contrib.taxi.run.TaxiConfigGroup;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.population.algorithms.PermissibleModesCalculatorImpl;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl.Builder;
import org.matsim.core.replanning.modules.ReRoute;
import org.matsim.core.replanning.modules.SubtourModeChoice;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
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
import org.matsim.vis.otfvis.OTFVisConfigGroup;

import org.matsim.santiago.utils.SantiagoScenarioConstants;

/**
 * @author benjamin
 */
public class SantiagoAVScenarioRunnerWithTaxi {


//	private static String inputPath = "D:\\matsim-eclipse\\shared-svn\\projects\\org.matsim.santiago\\scenario\\inputForMATSim\\AV_simulation\\";
//	private static String configFile = inputPath + "config_v2a_full.xml";

	public static void main(String args[]) {

		Config config = ConfigUtils.loadConfig(args, new DvrpConfigGroup(), new TaxiConfigGroup(),
				new OTFVisConfigGroup());

		config.controler().setOverwriteFileSetting(OverwriteFileSetting.overwriteExistingFiles);

		CommandLine cmd = ConfigUtils.getCommandLine( args ) ;
		boolean doModeChoice = Boolean.parseBoolean( cmd.getOption( SantiagoScenarioRunner.DOING_MODE_CHOICE ).orElse( "false" ) ) ;
		boolean mapActs2Links = Boolean.parseBoolean( cmd.getOption( SantiagoScenarioRunner.DOING_MODE_CHOICE ).orElse( "false" ) ) ;
		boolean cadyts = Boolean.parseBoolean( cmd.getOption( SantiagoScenarioRunner.USING_CADYTS ).orElse( "false" ) ) ;

		// ---

		Scenario scenario = ScenarioUtils.loadScenario(config);

		// ---

		Controler controler = new Controler(scenario);

		addTaxis(controler);

		// adding other network modes than car requires some router; here, the same values as for car are used
		setNetworkModeRouting(controler);

		// adding pt fare
		controler.getEvents().addHandler(new PTFareHandler(controler, doModeChoice, scenario.getPopulation()));

		// adding basic strategies for car and non-car users
		setBasicStrategiesForSubpopulations(config);

		// adding subtour mode choice strategies for car and non-car users
		if (doModeChoice) {
			setModeChoiceForSubpopulations(controler);
		}

		// mapping agents' activities to links on the road network to avoid being stuck on the transit network
		if (mapActs2Links) {
			SantiagoScenarioRunner.mapActivities2properLinks(scenario );
		}

		controler.addOverridingModule(new RoadPricingModule());

		if (cadyts) {
			controler.addOverridingModule(new CadytsCarModule());
			// include cadyts into the plan scoring (this will add the cadyts corrections to the scores)
			controler.setScoringFunctionFactory(new ScoringFunctionFactory() {
				@Inject
				CadytsContext cadytsContext;
				@Inject
				ScoringParametersForPerson parameters;

				@Override
				public ScoringFunction createNewScoringFunction(Person person) {
					final ScoringParameters params = parameters.getScoringParameters(person);

					SumScoringFunction scoringFunctionAccumulator = new SumScoringFunction();
					scoringFunctionAccumulator.addScoringFunction(
							new CharyparNagelLegScoring(params, controler.getScenario().getNetwork()));
					scoringFunctionAccumulator.addScoringFunction(new CharyparNagelActivityScoring(params));
					scoringFunctionAccumulator.addScoringFunction(new CharyparNagelAgentStuckScoring(params));

					final CadytsScoring<Link> scoringFunction = new CadytsScoring<>(person.getSelectedPlan(), config,
							cadytsContext);
					scoringFunction.setWeightOfCadytsCorrection(30. * config.planCalcScore().getBrainExpBeta());
					scoringFunctionAccumulator.addScoringFunction(scoringFunction);

					return scoringFunctionAccumulator;
				}
			});

		}

		// Run!
		controler.run();

	}

	private static void addTaxis(Controler controler) {
		// controler.addOverridingModule(new AbstractModule() {
		// @Override
		// public void install() {
		// addEventHandlerBinding().to(TaxiFareHandler.class).asEagerSingleton();
		// }
		// });
		String mode = TaxiConfigGroup.getSingleModeTaxiConfig(controler.getConfig()).getMode();
		controler.addOverridingModule(new DvrpModule());
		controler.addOverridingModule(new MultiModeTaxiModule());
		controler.configureQSimComponents(DvrpQSimComponents.activateModes(mode));

		boolean otfvis = false;
		if (otfvis) {
			controler.addOverridingModule(new OTFVisLiveModule());
		}
	}

	private static void setNetworkModeRouting(Controler controler) {
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());
				addTravelTimeBinding(SantiagoScenarioConstants.Modes.taxi.toString()).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(SantiagoScenarioConstants.Modes.taxi.toString()).to(
						carTravelDisutilityFactoryKey());
				// addTravelTimeBinding(SantiagoScenarioConstants.Modes.colectivo.toString()).to(networkTravelTime());
				// addTravelDisutilityFactoryBinding(SantiagoScenarioConstants.Modes.colectivo.toString()).to(carTravelDisutilityFactoryKey());
				addTravelTimeBinding(SantiagoScenarioConstants.Modes.other.toString()).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(SantiagoScenarioConstants.Modes.other.toString()).to(
						carTravelDisutilityFactoryKey());
			}
		});
	}

	private static void setBasicStrategiesForSubpopulations(Config config) {
		setReroute("carAvail", config);
		setChangeExp("carAvail", config);
		setReroute(null, config);
		setChangeExp(null, config);
	}

	private static void setChangeExp(String subpopName, Config config) {
		StrategySettings changeExpSettings = new StrategySettings();
		changeExpSettings.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta.toString());
		changeExpSettings.setSubpopulation(subpopName);
		// changeExpSettings.setWeight(0.85);
		changeExpSettings.setWeight(0.7); // TODO: BE AWARE OF THIS!!!
		config.strategy().addStrategySettings(changeExpSettings);
	}

	private static void setReroute(String subpopName, Config config) {
		StrategySettings reRouteSettings = new StrategySettings();
		reRouteSettings.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString());
		reRouteSettings.setSubpopulation(subpopName);
		reRouteSettings.setWeight(0.15);
		config.strategy().addStrategySettings(reRouteSettings);
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

		// TODO: somehow, there are agents for which the chaining does not work (e.g. agent 10002001)
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				Log.info("Adding SubtourModeChoice for agents with a car available...");
				final String[] availableModes1 = { TransportMode.car, TransportMode.walk, TransportMode.pt,
						SantiagoScenarioConstants.COLECTIVOMODE };
				final String[] chainBasedModes1 = { TransportMode.car };
				addPlanStrategyBinding(nameMcCarAvail).toProvider(
						new SubtourModeChoiceProvider(availableModes1, chainBasedModes1));
				Log.info("Adding SubtourModeChoice for the rest of the agents...");
				final String[] availableModes2 = { TransportMode.walk, TransportMode.pt,
						SantiagoScenarioConstants.COLECTIVOMODE };
				final String[] chainBasedModes2 = {};
				addPlanStrategyBinding(nameMcNonCarAvail).toProvider(
						new SubtourModeChoiceProvider(availableModes2, chainBasedModes2));
			}
		});

	}

	/**
	 * @author benjamin
	 */
	private static final class SubtourModeChoiceProvider implements javax.inject.Provider<PlanStrategy> {
		@Inject
		Scenario scenario;
		@Inject
		Provider<TripRouter> tripRouterProvider;
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
//			builder.addStrategyModule(
//					new SubtourModeChoice(scenario.getConfig().global().getNumberOfThreads(), availableModes,
//							chainBasedModes, false, 0.0, // using value of 0.0 (=default) for backward compatibility
//							tripRouterProvider));
					builder.addStrategyModule(new SubtourModeChoice(scenario.getConfig().global(), scenario.getConfig().subtourModeChoice(),
							new PermissibleModesCalculatorImpl(scenario.getConfig()) ) );

			builder.addStrategyModule(new ReRoute(scenario, tripRouterProvider));
			return builder.build();
		}
	}
}
