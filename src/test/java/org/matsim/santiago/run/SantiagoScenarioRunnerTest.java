package org.matsim.santiago.run;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.testcases.MatsimTestUtils;

import java.net.URL;

public class SantiagoScenarioRunnerTest{

	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	@Test
	public void mainWithEquil(){

		String outDir = utils.getOutputDirectory() ;

		String[] args = {
				"scenarios/equil/config.xml",
				"--" + SantiagoScenarioRunner.DOING_MODE_CHOICE + "=false",
				"--" + SantiagoScenarioRunner.MAPPING_ACTIVITIES_TO_CAR_LINKS + "=false",
				"--" + SantiagoScenarioRunner.USING_CADYTS + "=false",
				"--config:controler.outputDirectory=" + outDir,
				"--config:controler.lastIteration=" + 10
		} ;

		try {
			SantiagoScenarioRunner.main( args );
		} catch ( Exception ee ) {
			ee.printStackTrace();
			Assert.fail();
		}

	}

	// I tried a 0.1pct variant, but it is not visibly faster than the 1pct variant.  In contrast, switching off transit in the mobsim helps a lot.  kai, nov'19

	@Test
	public void santiago1pctWoTransitInMobsim0it(){

		String[] args = {
//			  "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml", // config file
//			  "../..//public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml", // config file
				"scenarios/santiago-v2b/config_baseCase10pct.xml",
				"--" + SantiagoScenarioRunner.DOING_MODE_CHOICE + "=false",
				"--" + SantiagoScenarioRunner.MAPPING_ACTIVITIES_TO_CAR_LINKS + "=false",
				"--" + SantiagoScenarioRunner.USING_CADYTS + "=false",
		} ;

		try {


			Config config = SantiagoScenarioRunner.prepareConfig( args );
			config.setContext( new URL( "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/" ) );

			config.controler().setLastIteration( 0 );

			config.plans().setInputFile( "input/randomized_expanded_plans_with_attributes_1pct_routed.xml.gz" );  // relative to config file dir

			config.qsim().setFlowCapFactor( 0.01 );
			config.qsim().setStorageCapFactor( 0.01 );

			config.transit().setUsingTransitInMobsim( false ); // !!!

			config.controler().setOutputDirectory( utils.getOutputDirectory() );

			SantiagoScenarioRunner.run( config, args) ;

		} catch ( Exception ee ) {
			ee.printStackTrace();
			Assert.fail();
		}

	}

	@Test
	public void santiago1pct0it(){

		String[] args = {
//			  "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml", // config file
//			  "../..//public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml", // config file
				"scenarios/santiago-v2b/config_baseCase10pct.xml",
				"--" + SantiagoScenarioRunner.DOING_MODE_CHOICE + "=false",
				"--" + SantiagoScenarioRunner.MAPPING_ACTIVITIES_TO_CAR_LINKS + "=false",
				"--" + SantiagoScenarioRunner.USING_CADYTS + "=false"
		} ;

		try {

			Config config = SantiagoScenarioRunner.prepareConfig( args );
			config.setContext( new URL( "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/" ) );

			config.controler().setOutputDirectory( utils.getOutputDirectory() );
			config.controler().setLastIteration( 0 );

			config.plans().setInputFile( "input/randomized_expanded_plans_with_attributes_1pct_routed.xml.gz" );  // relative to config file dir

			config.qsim().setFlowCapFactor( 0.01 );
			config.qsim().setStorageCapFactor( 0.01 );

			config.controler().setOutputDirectory( utils.getOutputDirectory() );

			SantiagoScenarioRunner.run(  config, args );

		} catch ( Exception ee ) {
			ee.printStackTrace();
			Assert.fail();
		}

	}

	@Test
	public void santiago1pct2it(){

		String[] args = {
//			  "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml", // config file
//			  "../..//public-svn/matsim/scenarios/countries/cl/santiago/v2b/santiago/config_baseCase10pct.xml", // config file
				"scenarios/santiago-v2b/config_baseCase10pct.xml",
				"--" + SantiagoScenarioRunner.DOING_MODE_CHOICE + "=false",
				"--" + SantiagoScenarioRunner.MAPPING_ACTIVITIES_TO_CAR_LINKS + "=false",
				"--" + SantiagoScenarioRunner.USING_CADYTS + "=false"
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

			config.controler().setOutputDirectory( utils.getOutputDirectory() );

			SantiagoScenarioRunner.run(  config, args );

		} catch ( Exception ee ) {
			ee.printStackTrace();
			Assert.fail();
		}

	}
}
