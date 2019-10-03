package org.matsim.santiago.analysis.travelDistances;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.utils.io.IOUtils;


import org.matsim.santiago.analysis.eventHandlers.travelDistances.SantiagoTollwayDistanceHandler;

public class SantiagoTollwayDistanceAnalysis {
	
	private String runDir;	
	private String outputDir;
	private String analysisDir;
	private List<Id<Person>> stuckAgents;
	private String shapeFile;
	
	public SantiagoTollwayDistanceAnalysis(String caseName, String stepName, List<Id<Person>> stuckAgents, String shapeFile){
		this.runDir = "../../../runs-svn/org.matsim.santiago/" + caseName + "/";
		this.outputDir = runDir + "outputOf" + stepName + "/";
		this.analysisDir = outputDir + "analysis/";	
		this.stuckAgents = stuckAgents;
		this.shapeFile = shapeFile;
	}
	
	private void createDir(File file) {
		file.mkdirs();	
	}
	
	public void writeFileForTollwayDistances(int it, int itAux){

		File analysisDir = new File(this.analysisDir);
		if(!analysisDir.exists()) createDir(analysisDir);

		String configFile = outputDir + "output_config.xml.gz";
		String netFile = outputDir + "output_network.xml.gz";
		String eventsFile = outputDir + "ITERS/it." + String.valueOf(it) + "/" + String.valueOf(it) + ".events.xml.gz";
		String outputFile = this.analysisDir + String.valueOf(itAux) + ".tollwayDistances.txt";

		Config config = ConfigUtils.loadConfig(configFile);
		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(netFile);

		SantiagoTollwayDistanceHandler handler = new SantiagoTollwayDistanceHandler(config,network,stuckAgents,shapeFile);
		EventsManager events = EventsUtils.createEventsManager();
		events.addHandler(handler);
		MatsimEventsReader reader = new MatsimEventsReader(events);
		reader.readFile(eventsFile);

		SortedMap<String, ArrayList<String>> tollwayDistances = handler.getPeriodLinkIdLengthTollway();

		try (BufferedWriter writer = IOUtils.getBufferedWriter(outputFile)) {
			writer.write("concesion-midHr\tlinkId-Length\n");

			for(String concesionPeriod : tollwayDistances.keySet()){				
				for (String linkWithLength: tollwayDistances.get(concesionPeriod)){
						writer.write(concesionPeriod+"\t"+linkWithLength+"\n");
				}
			}

			writer.close();
		} catch (Exception e) {
			throw new RuntimeException("Data is not written. Reason "+e );
		}


	}

}
