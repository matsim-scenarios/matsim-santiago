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

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.roadpricing.RoadPricingModule;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;

import org.matsim.santiago.utils.SantiagoScenarioConstants;
import org.matsim.santiago.analysis.modalShareFromEvents.ModalShareFromEvents;
import org.matsim.santiago.colectivos.PostProcessModalShare;
import org.matsim.santiago.colectivos.router.ColectivoModule;

/**
 * @author benjamin
 *
 */
public class SantiagoScenarioRunnerFelix {

	static final String COLECTIVO_ASC = "colectivoASC" ;

//	private static final String inputPath = "C:/Users/Felix/Documents/Bachelor/Santiago de Chile/v3/input/";

	public static void main(String args[]){

		Config config = ConfigUtils.loadConfig( args ) ;

		CommandLine cmd = ConfigUtils.getCommandLine( args ) ;

		boolean doModeChoice = Boolean.parseBoolean( cmd.getOption( SantiagoScenarioRunner.DOING_MODE_CHOICE ).orElse( "false" ) ) ;
		double colectivoASC = Double.parseDouble( cmd.getOption( COLECTIVO_ASC ).orElse( "0" ) ) ;
		boolean mapActs2Links = Boolean.parseBoolean( cmd.getOption( SantiagoScenarioRunner.MAPPING_ACTIVITIES_TO_CAR_LINKS ).orElse( "false" ) ) ;

		// ---

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
			SantiagoScenarioRunner.setBasicStrategiesForSubpopulations(controler);
			
			// adding subtour mode choice strategies for car and non-car users			
			if(doModeChoice) SantiagoScenarioRunner.setModeChoiceForSubpopulations(controler);
			
			// mapping agents' activities to links on the road network to avoid being stuck on the transit network
			if(mapActs2Links) SantiagoScenarioRunner.mapActivities2properLinks(scenario );
			

			controler.addOverridingModule(new RoadPricingModule());
	
			//Run!
			controler.run();
			
			String[] outputFolder = {controler.getControlerIO().getOutputPath()+"/"};
			PostProcessModalShare.main(outputFolder);
			
			ModalShareFromEvents msg = new ModalShareFromEvents(outputFolder[0]+"output_events.xml.gz");
			msg.run();
			msg.writeResults(outputFolder[0]+"output_modeShare.txt");
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

}
