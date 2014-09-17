/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.policy.notifier;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.basic.AbstractPolicy;

/** performs an HTTP call to inform of problems and resolution */
@Catalog(name="Web Hook Notifier", description="Policy which invokes a web hook when the service state indicates problems")
public class HttpNotifierPolicy extends AbstractPolicy implements SensorEventListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpNotifierPolicy.class);

    public HttpNotifierPolicy() {}
    public HttpNotifierPolicy(Map<String,?> flags) { super(flags); }

    @Override
    public void setEntity(final EntityLocal entity) {
        super.setEntity(entity);

        subscribe(entity, Attributes.SERVICE_STATE_ACTUAL, this);
    }

    @Override
    public void onEvent(SensorEvent<Object> event) {
        if (Lifecycle.ON_FIRE.equals( event.getValue() )) {
            // on fire
            
        }
    }

}
