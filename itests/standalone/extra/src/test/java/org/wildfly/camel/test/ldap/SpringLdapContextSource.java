/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wildfly.camel.test.ldap;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.ldap.core.support.LdapContextSource;
import org.wildfly.camel.test.common.utils.AvailablePortFinder;

public class SpringLdapContextSource extends LdapContextSource {

    public SpringLdapContextSource() {
        try {
            int port = Integer.parseInt(AvailablePortFinder.readServerData("ldap-port"));
            setUrl("ldap://" + InetAddress.getLocalHost().getHostAddress() + ":" + port);
        } catch (UnknownHostException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
