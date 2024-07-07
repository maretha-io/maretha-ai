/*
 * Copyright 2023 Maretha Solutions LLC - https://maretha.io.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.maretha.cp.core.stream;

import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

import java.util.Arrays;
import java.util.Map;

import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.DONE_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

public class StreamDocumentProcessor implements StreamProcessorTopology {
    public static final String ACTION_NAME = "cpStream";

    public static final String ACTION_FULL_NAME = "bulk/" + ACTION_NAME;

    @Override
    public Topology getTopology(Map<String, String> map) {
        return Topology.builder()
                       .addComputation(CPInteractionComputation::new,
                               Arrays.asList(INPUT_1 + ":" + ACTION_FULL_NAME, OUTPUT_1 + ":" + DONE_STREAM))
                       .build();
    }
}
