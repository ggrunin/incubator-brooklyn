package brooklyn.rest.resources;

import brooklyn.rest.BaseResourceTest;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.yammer.dropwizard.logging.Log;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import javax.ws.rs.core.Response;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

@Test(singleThreaded = true)
public class ApplicationResourceTest extends BaseResourceTest {

  private ApplicationManager manager;
  private ExecutorService executorService;

  private final ApplicationSpec redisSpec = new ApplicationSpec("redis-app",
      ImmutableSet.of(new EntitySpec("redis-ent", "brooklyn.entity.nosql.redis.RedisStore")),
      ImmutableSet.of("/locations/0"));

  @Override
  protected void setUpResources() throws Exception {
    executorService = Executors.newCachedThreadPool();
    manager = new ApplicationManager(LocationStore.withLocalhost(), executorService);

    addResource(new ApplicationResource(manager, new EntityResource()));
    addResource(new SensorResource(manager));
    addResource(new EffectorResource(manager));
  }

  @AfterClass
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    manager.stop();
    executorService.shutdown();
  }

  @Test
  public void testGetUndefinedApplication() {
    try {
      client().resource("/applications/dummy-not-found").get(Application.class);
    } catch (UniformInterfaceException e) {
      assertEquals(e.getResponse().getStatus(), 404);
    }
  }

  @Test
  public void testDeployRedisApplication() throws InterruptedException, TimeoutException {
    ClientResponse response = client().resource("/applications")
        .post(ClientResponse.class, redisSpec);

    assertEquals(manager.registry().size(), 1);
    assertEquals(response.getLocation().getPath(), "/applications/redis-app");

    waitForApplicationToBeRunning(response);
  }

  private void waitForApplicationToBeRunning(ClientResponse response) throws InterruptedException, TimeoutException {
    int count = 0;
    while (getApplicationStatus(response.getLocation()) != Application.Status.RUNNING) {
      Thread.sleep(7000);
      count += 1;
      if (count == 20) {
        throw new TimeoutException("Taking to long to get to RUNNING.");
      }
    }
  }

  private Application.Status getApplicationStatus(URI uri) {
    return client().resource(uri).get(Application.class).getStatus();
  }

  @Test(dependsOnMethods = "testDeployRedisApplication")
  public void testListApplications() {
    Set<Application> applications = client().resource("/applications")
        .get(new GenericType<Set<Application>>() {
        });
    assertEquals(applications.size(), 1);
    assertEquals(Iterables.get(applications, 0).getSpec(), redisSpec);
  }

  @Test(dependsOnMethods = "testDeployRedisApplication")
  public void testListSensors() {
    Map<String, Set<URI>> sensors = client().resource("/applications/redis-app/sensors")
        .get(new GenericType<Map<String, Set<URI>>>() {
        });
    assertTrue(sensors.containsKey("redis-ent"));
    assertTrue(sensors.get("redis-ent").contains(
        URI.create("/applications/redis-app/sensors/redis-ent/redis.uptime")));
  }

  @Test(dependsOnMethods = "testListSensors")
  public void testReadAllSensors() {
    Map<String, Set<URI>> sensors = client().resource("/applications/redis-app/sensors")
        .get(new GenericType<Map<String, Set<URI>>>() {
        });

    Map<String, String> readings = Maps.newHashMap();
    for (URI ref : sensors.get("redis-ent")) {
      readings.put(ref.toString(), client().resource(ref).get(String.class));
    }

    assertEquals(readings.get("/applications/redis-app/sensors/redis-ent/service.state"), "running");
    assertEquals(readings.get("/applications/redis-app/sensors/redis-ent/redis.port"), "6379");
  }

  @Test(dependsOnMethods = {"testReadAllSensors", "testListApplications"})
  public void testDeleteApplication() throws TimeoutException, InterruptedException {
    ClientResponse response = client().resource("/applications/redis-app")
        .delete(ClientResponse.class);

    waitForPageNotFoundResponse("/applications/redis-app");

    assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
    assertEquals(manager.registry().size(), 0);
  }

  private void waitForPageNotFoundResponse(String resource) throws InterruptedException, TimeoutException {
    int count = 0;
    while (true) {
      try {
        client().resource(resource).get(Application.class);

      } catch (UniformInterfaceException e) {
        if (e.getResponse().getStatus() == 404) {
          break;
        }
      }
      Thread.sleep(5000);
      count += 1;
      if (count > 20) {
        throw new TimeoutException("Timeout while waiting for 404 on " + resource);
      }
    }
  }
}
