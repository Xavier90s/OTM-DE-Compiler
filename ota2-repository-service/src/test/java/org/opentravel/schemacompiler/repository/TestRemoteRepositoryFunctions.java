/**
 * Copyright (C) 2014 OpenTravel Alliance (info@opentravel.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opentravel.schemacompiler.repository;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.ws.rs.core.Response;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opentravel.ns.ota2.repositoryinfo_v01_00.RepositoryPermission;
import org.opentravel.schemacompiler.repository.RemoteRepository;

/**
 * Verifies the operation of items published to a remote repository.
 * 
 * @author S. Livezey
 */
public class TestRemoteRepositoryFunctions extends TestRepositoryFunctions {

    @BeforeClass
    public static void setupTests() throws Exception {
        setupWorkInProcessArea(TestRemoteRepositoryFunctions.class);
        startTestServer("empty-repository", 9291, TestRemoteRepositoryFunctions.class);
    }

    @AfterClass
    public static void tearDownTests() throws Exception {
        shutdownTestServer();
    }

    @Test
    public void testUserAuthorizations() throws Exception {
        String readNS = "http://www.OpenTravel.org";
        String writeNS = "http://www.OpenTravel.org/ns/OTA2/SchemaCompiler";
        RepositoryPermission permission1 = ((RemoteRepository) testRepository.get())
                .getUserAuthorization(readNS);
        RepositoryPermission permission2 = ((RemoteRepository) testRepository.get())
                .getUserAuthorization(writeNS);

        assertEquals(RepositoryPermission.READ_FINAL, permission1);
        assertEquals(RepositoryPermission.WRITE, permission2);
    }

    @Test
    public void testGetUserAuthorizationWithNamespace() throws ClientProtocolException, IOException {
        CloseableHttpResponse ret = doGet(((RemoteRepository) testRepository.get())
                .getEndpointUrl()
                + "/service/user-authorization?baseNamespace=http://www.OpenTravel.org");
        assertEquals(Response.Status.OK.getStatusCode(), ret.getStatusLine().getStatusCode());
    }

    @Test
    public void testGetUserAuthorizationForMissingNamespace() throws ClientProtocolException,
            IOException {
        CloseableHttpResponse ret = doGet(((RemoteRepository) testRepository.get())
                .getEndpointUrl() + "/service/user-authorization");
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), ret.getStatusLine()
                .getStatusCode());
    }

    private CloseableHttpResponse doGet(String url) throws ClientProtocolException, IOException {
        HttpGet get = new HttpGet(url);
        return HttpClients.createDefault().execute(get);
    }

}
