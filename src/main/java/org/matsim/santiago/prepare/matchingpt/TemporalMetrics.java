/**
 * Copyright (C) 2015, BMW Car IT GmbH
 * Author: Stefan Holder (stefan.holder@bmw.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matsim.santiago.prepare.matchingpt;

/**
 * Defines temporal metrics between location measurements.
 *
 * @param <O> location measurement type, which corresponds to the HMM observation.
 */
interface TemporalMetrics<O> {

    /**
     * Returns the time difference in seconds between the specified location measurements.
     * The time difference is positive if m2 is later than m1 and negative if m1 is later than m2.
     *
     * This is needed to compute the normalized transition metric defined in
     * {@link MapMatchingHmmProbabilities#normalizedTransitionMetric(Object, Object, Object, Object)}.
     */
    public double timeDifference(O m1, O m2);
}
