/* *********************************************************************** *
 * project: org.matsim.*
 * TransitRouterImplFactory.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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

package org.matsim.santiago.colectivos.router;

import org.matsim.core.config.Config;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterImpl;
import org.matsim.pt.router.TransitRouterNetwork;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import com.google.inject.name.Named;

import org.matsim.santiago.utils.SantiagoScenarioConstants;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * @author mrieser
 */
@Singleton
public class ColectivoTransitRouterImplFactory implements Provider<TransitRouter> {

	private final TransitRouterConfig config;
	private final TransitRouterNetwork routerNetwork;
	private final PreparedTransitSchedule preparedTransitSchedule;

	@Inject
	ColectivoTransitRouterImplFactory(final @Named(SantiagoScenarioConstants.COLECTIVOMODE)TransitSchedule schedule, final Config config) {
		this(schedule, new TransitRouterConfig(
				config.planCalcScore(),
				config.plansCalcRoute(),
				config.transitRouter(),
				config.vspExperimental()));
	}

	public ColectivoTransitRouterImplFactory(final TransitSchedule schedule, final TransitRouterConfig config) {
		this.config = config;
		this.routerNetwork = TransitRouterNetwork.createFromSchedule(schedule, this.config.getBeelineWalkConnectionDistance());
		this.preparedTransitSchedule = new PreparedTransitSchedule(schedule);
	}

	@Override
	public TransitRouter get() {
		TransitRouterNetworkTravelTimeAndDisutility ttCalculator = new TransitRouterNetworkTravelTimeAndDisutility(this.config, this.preparedTransitSchedule);
		return new TransitRouterImpl(this.config, this.preparedTransitSchedule, this.routerNetwork, ttCalculator, ttCalculator);
	}
	
}
