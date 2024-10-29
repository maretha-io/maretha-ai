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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class CPInteractionComputation extends AbstractBulkComputation {

    private static final Log log = LogFactory.getLog(CPInteractionComputation.class);

    public CPInteractionComputation() {
        super(StreamDocumentProcessor.ACTION_FULL_NAME);
    }

    @Override
    protected void compute(CoreSession session, List<String> texts, Map<String, Serializable> properties) {
//        texts.forEach(text -> {
//            String interactionResult = Framework.getService(CPServiceInterface.class).getInteractionResult(text, data);
//            log.debug("InteractionResult: " + interactionResult);
//        });
    }

}
