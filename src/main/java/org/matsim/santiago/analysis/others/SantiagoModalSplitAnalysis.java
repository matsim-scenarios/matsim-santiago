package org.matsim.santiago.analysis.others;

import java.io.File;

import org.matsim.santiago.analysis.modalShareFromEvents.ModalShareFromEvents;


public class SantiagoModalSplitAnalysis {
	private String runDir;	
	private String outputDir;
	private String analysisDir;
//	private List<Id<Person>> stuckAgents;
	
	public SantiagoModalSplitAnalysis(String caseName, String stepName/*, List<Id<Person>> stuckAgents*/){
		this.runDir = "../../../runs-svn/org.matsim.santiago/" + caseName + "/";
		this.outputDir = runDir + "outputOf" + stepName + "/";
		this.analysisDir = outputDir + "analysis/";	
//		this.stuckAgents=stuckAgents;
		
	}
	
	private void createDir(File file) {
		file.mkdirs();	
	}
	
	
	/**
	 * writeModalShare uses ModalShareFromEvents from playground.agarwalamit.analysis.modalShare.ModalShareFromEvents. Stuck agents are considered, be aware.
	 */
	public void writeModalShare(int it, int itAux){

		File analysisDir = new File(this.analysisDir);
		if(!analysisDir.exists()) createDir(analysisDir);
		String eventsFile = outputDir + "ITERS/it." + String.valueOf(it) + "/" + String.valueOf(it) + ".events.xml.gz";
		ModalShareFromEvents msc = new ModalShareFromEvents(eventsFile);
		msc.run();
		String outputFile = this.analysisDir + String.valueOf(itAux) + ".modalSplit.txt";
		msc.writeResults(outputFile);


	}
}
