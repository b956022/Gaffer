/*
 * Copyright 2017-2020 Crown Copyright
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
 */

package uk.gov.gchq.gaffer.federatedstore.operation.handler.impl;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;

import uk.gov.gchq.gaffer.commonutil.iterable.ChainedIterable;
import uk.gov.gchq.gaffer.commonutil.iterable.CloseableIterable;
import uk.gov.gchq.gaffer.commonutil.iterable.WrappedCloseableIterable;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.data.element.Entity;
import uk.gov.gchq.gaffer.federatedstore.operation.handler.FederatedOperationOutputHandler;
import uk.gov.gchq.gaffer.federatedstore.operation.handler.FederatedOperationOutputHandlerTest;
import uk.gov.gchq.gaffer.operation.impl.get.GetElements;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FederatedGetElementsHandlerTest extends FederatedOperationOutputHandlerTest<GetElements, CloseableIterable<? extends Element>> {


    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        o1 = new WrappedCloseableIterable<>(Lists.<Element>newArrayList(new Entity.Builder().group(TEST_ENTITY)
                .property(PROPERTY_TYPE, 1)
                .build()));
        o2 = new WrappedCloseableIterable<>(Lists.newArrayList(new Entity.Builder().group(TEST_ENTITY)
                .property(PROPERTY_TYPE, 2)
                .build()));
        o3 = new WrappedCloseableIterable<>(Lists.newArrayList(new Entity.Builder().group(TEST_ENTITY)
                .property(PROPERTY_TYPE, 3)
                .build()));
        o4 = new WrappedCloseableIterable<>(Lists.newArrayList(new Entity.Builder().group(TEST_ENTITY)
                .property(PROPERTY_TYPE, 2)
                .build()));
    }

    @Override
    protected FederatedOperationOutputHandler<GetElements, CloseableIterable<? extends Element>> getFederatedHandler() {
        return new FederatedGetElementsHandler();
    }

    @Override
    protected GetElements getExampleOperation() {
        return new GetElements.Builder().build();
    }

    @Override
    protected boolean validateMergeResultsFromFieldObjects(final CloseableIterable<? extends Element> result, final Object... resultParts) {
        assertNotNull(result);
        final Iterable[] resultPartItrs = Arrays.copyOf(resultParts, resultParts.length, Iterable[].class);
        final ArrayList<Object> elements = Lists.newArrayList(new ChainedIterable<>(resultPartItrs));
        int i = 0;
        for (Element e : result) {
            assertTrue(e instanceof Entity);
            elements.contains(e);
            i++;
        }
        assertEquals(elements.size(), i);
        return true;
    }
}
