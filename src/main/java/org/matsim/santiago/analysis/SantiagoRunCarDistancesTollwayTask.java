package org.matsim.santiago.analysis;

import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

import org.matsim.santiago.analysis.others.SantiagoStuckAgentsAnalysis;
import org.matsim.santiago.analysis.travelDistances.SantiagoSecondaryDistanceAnalysis;
import org.matsim.santiago.analysis.travelDistances.SantiagoTollwayDistanceAnalysis;

/**
 * 
 */

public class SantiagoRunCarDistancesTollwayTask {
	
	private static final String CASE_NAME = "baseCase1pct";
	private static final String STEP_NAME = "Step1";
	private static final int IT_TO_EVALUATE = 500; //From the local counter
	private static final int REFERENCE_IT = 100; //Reference iteration (same as first iteration of the step)
	private static final String SHAPE_FILE = "../../../km_tollways/1_MODIFIED/network_merged_cl_car_links_modified.shp";
	
	public static void main (String[]args){
		
		int it = IT_TO_EVALUATE;
		int itAux = IT_TO_EVALUATE+REFERENCE_IT;
		
		SantiagoStuckAgentsAnalysis stuckAnalysis = new SantiagoStuckAgentsAnalysis(CASE_NAME, STEP_NAME);
		List<Id<Person>> stuckAgents = stuckAnalysis.getStuckAgents(it);
		
		SantiagoTollwayDistanceAnalysis tollwayAnalysis = new SantiagoTollwayDistanceAnalysis(CASE_NAME,STEP_NAME, stuckAgents, SHAPE_FILE);
		tollwayAnalysis.writeFileForTollwayDistances(it, itAux);
		
		SantiagoSecondaryDistanceAnalysis secondaryAnalysis = new SantiagoSecondaryDistanceAnalysis(CASE_NAME,STEP_NAME, stuckAgents, SHAPE_FILE);
		secondaryAnalysis.writeFileForSecondaryDistances(it, itAux);
		
	}
}
