package org.matsim.santiago.run;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.net.URL;

public class SantiagoScenarioRunnerTest{

	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	@Test
	public void mainWithEquil(){

		String[] args = {
			  "scenarios/equil/config.xml",
			  "gantries.xml", // toll links.  relative to config file location
			  "0", // policy; 0=base
			  "3", // sigma
			  "false", // do mode choice
			  "false", // map activities to links
			  "false" // cadyts
		} ;

		try {
			SantiagoScenarioRunner.main( args );
		} catch ( Exception ee ) {
			ee.printStackTrace();
			Assert.fail();
		}

	}

	@Test
	public void santiago1pct0it(){

		String[] args = {
			  "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml", // config file
//			  "../..//public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml", // config file
			  "input/gantries.xml", // toll links.  relative to config file location
			  "0", // policy; 0=base
			  "3", // sigma
			  "false", // do mode choice
			  "false", // map activities to links
			  "false" // cadyts
		} ;

		try {

			Config config = SantiagoScenarioRunner.prepareConfig( args );

			config.setContext( new URL( "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/" ) );

			config.controler().setOutputDirectory( utils.getOutputDirectory() );
			config.controler().setLastIteration( 0 );

//			config.plans().setInputFile( "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/input/randomized_expanded_plans_with_attributes_1pct.xml.gz" );
			config.plans().setInputFile( "input/randomized_expanded_plans_with_attributes_1pct_routed.xml.gz" );  // relative to config file dir

			config.qsim().setFlowCapFactor( 0.01 );
			config.qsim().setStorageCapFactor( 0.01 );

			Scenario scenario = SantiagoScenarioRunner.prepareScenario( config ) ;

			Controler controler = SantiagoScenarioRunner.prepareControler( scenario ) ;

			controler.run() ;

		} catch ( Exception ee ) {
			ee.printStackTrace();
			Assert.fail();
		}

	}

	@Test
	public void santiago1pct2it(){

		String[] args = {
			  "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml", // config file
//			  "../..//public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml", // config file
			  "input/gantries.xml", // toll links.  relative to config file location
			  "0", // policy; 0=base
			  "3", // sigma
			  "false", // do mode choice
			  "false", // map activities to links
			  "false" // cadyts
		} ;

		try {

			Config config = SantiagoScenarioRunner.prepareConfig( args );

			config.setContext( new URL( "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/" ) );

			config.controler().setOutputDirectory( utils.getOutputDirectory() );
			config.controler().setLastIteration( 2 );

//			config.plans().setInputFile( "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/input/randomized_expanded_plans_with_attributes_1pct.xml.gz" );
			config.plans().setInputFile( "input/randomized_expanded_plans_with_attributes_1pct_routed.xml.gz" );  // relative to config file dir

			config.qsim().setFlowCapFactor( 0.01 );
			config.qsim().setStorageCapFactor( 0.01 );

			Scenario scenario = SantiagoScenarioRunner.prepareScenario( config ) ;

			Controler controler = SantiagoScenarioRunner.prepareControler( scenario ) ;

			controler.run() ;

		} catch ( Exception ee ) {
			ee.printStackTrace();
			Assert.fail();
		}

	}
}
