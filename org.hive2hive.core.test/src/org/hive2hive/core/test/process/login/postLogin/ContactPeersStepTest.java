package org.hive2hive.core.test.process.login.postLogin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;

import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.model.LocationEntry;
import org.hive2hive.core.model.Locations;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.network.NetworkUtils;
import org.hive2hive.core.process.login.ContactPeersStep;
import org.hive2hive.core.process.login.PostLoginProcess;
import org.hive2hive.core.process.login.PostLoginProcessContext;
import org.hive2hive.core.test.H2HJUnitTest;
import org.hive2hive.core.test.H2HWaiter;
import org.hive2hive.core.test.network.NetworkTestUtil;
import org.hive2hive.core.test.process.TestProcessListener;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test class to check if the {@link ContactPeersStep} process step works correctly. Only this process step
 * will be tested in a own process environment.
 * 
 * node 0 is the new client node
 * node 1, 2, 3 are other alive client nodes
 * node 4, 5 are not responding client nodes
 * 
 * ranking from smallest to greatest node id:
 * C, B, A, F, E, G, A, D
 * 
 * @author Seppi
 */
public class ContactPeersStepTest extends H2HJUnitTest {

	private static final int networkSize = 6;
	// in seconds
	private static final int maxWaitingTimeTillFail = 3;
	private static List<NetworkManager> network;
	private static String userId = "user id";

	private Locations result = null;

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = ContactPeersStepTest.class;
		beforeClass();
		network = NetworkTestUtil.createNetwork(networkSize);
		// assign to each node the same key pair (simulating same user)
		NetworkTestUtil.createSameKeyPair(network);
		// assign to a subset of the client nodes a rejecting message reply handler
		network.get(4).getConnection().getPeer().setObjectDataReply(new DenyingMessageReplyHandler());
		network.get(5).getConnection().getPeer().setObjectDataReply(new DenyingMessageReplyHandler());
	}

	private boolean isMasterClient(Locations locations, PeerAddress client) {
		ArrayList<PeerAddress> list = new ArrayList<PeerAddress>();
		for (LocationEntry entry : locations.getLocationEntries())
			list.add(entry.getAddress());
		PeerAddress master = NetworkUtils.choseFirstPeerAddress(list);
		return (master.equals(client));
	}

	/**
	 * All client nodes are alive.
	 */
	@Test
	public void allClientsAreAlive() {
		Locations fakedLocations = new Locations(userId);
		fakedLocations.addEntry(new LocationEntry(network.get(0).getPeerAddress()));
		// responding nodes
		fakedLocations.addEntry(new LocationEntry(network.get(1).getPeerAddress()));
		fakedLocations.addEntry(new LocationEntry(network.get(2).getPeerAddress()));
		fakedLocations.addEntry(new LocationEntry(network.get(3).getPeerAddress()));

		runProcessStep(fakedLocations, isMasterClient(fakedLocations, network.get(0).getPeerAddress()));

		assertEquals(4, result.getLocationEntries().size());
		LocationEntry newClientsEntry = null;
		for (LocationEntry locationEntry : result.getLocationEntries()) {
			if (locationEntry.getAddress().equals(network.get(0).getPeerAddress())) {
				newClientsEntry = locationEntry;
				break;
			}
		}
		assertNotNull(newClientsEntry);
	}

	/**
	 * Some client nodes are offline.
	 */
	@Test
	public void notAllClientsAreAlive() {
		Locations fakedLocations = new Locations(userId);
		fakedLocations.addEntry(new LocationEntry(network.get(0).getPeerAddress()));
		fakedLocations.addEntry(new LocationEntry(network.get(1).getPeerAddress()));
		// not responding nodes
		fakedLocations.addEntry(new LocationEntry(network.get(4).getPeerAddress()));
		fakedLocations.addEntry(new LocationEntry(network.get(5).getPeerAddress()));

		runProcessStep(fakedLocations, isMasterClient(fakedLocations, network.get(0).getPeerAddress()));

		assertEquals(2, result.getLocationEntries().size());
		LocationEntry newClientsEntry = null;
		for (LocationEntry locationEntry : result.getLocationEntries()) {
			if (locationEntry.getAddress().equals(network.get(0).getPeerAddress())) {
				newClientsEntry = locationEntry;
				break;
			}
		}
		assertNotNull(newClientsEntry);
	}

	/**
	 * No other clients are or have been online.
	 */
	@Test
	public void noOtherClientsOrDeadClients() {
		Locations fakedLocations = new Locations(userId);
		fakedLocations.addEntry(new LocationEntry(network.get(0).getPeerAddress()));

		runProcessStep(fakedLocations, true);

		assertEquals(1, result.getLocationEntries().size());
		assertEquals(network.get(0).getPeerAddress(), result.getLocationEntries().iterator().next()
				.getAddress());
	}

	/**
	 * No client is responding.
	 */
	@Test
	public void allOtherClientsAreDead() {
		Locations fakedLocations = new Locations(userId);
		fakedLocations.addEntry(new LocationEntry(network.get(0).getPeerAddress()));
		// not responding nodes
		fakedLocations.addEntry(new LocationEntry(network.get(4).getPeerAddress()));
		fakedLocations.addEntry(new LocationEntry(network.get(5).getPeerAddress()));

		runProcessStep(fakedLocations, true);

		assertEquals(1, result.getLocationEntries().size());
		assertEquals(network.get(0).getPeerAddress(), result.getLocationEntries().iterator().next()
				.getAddress());
	}

	/**
	 * Received an empty location map.
	 */
	@Test
	public void emptyLocations() {
		Locations fakedLocations = new Locations(userId);

		runProcessStep(fakedLocations, true);

		assertEquals(1, result.getLocationEntries().size());
		assertEquals(network.get(0).getPeerAddress(), result.getLocationEntries().iterator().next()
				.getAddress());
	}

	/**
	 * Received a location map without own location entry.
	 */
	@Test
	public void notCompleteLocations() {
		Locations fakedLocations = new Locations(userId);
		fakedLocations.addEntry(new LocationEntry(network.get(1).getPeerAddress()));

		runProcessStep(fakedLocations, isMasterClient(fakedLocations, network.get(0).getPeerAddress()));

		assertEquals(2, result.getLocationEntries().size());
		LocationEntry newClientsEntry = null;
		for (LocationEntry locationEntry : result.getLocationEntries()) {
			if (locationEntry.getAddress().equals(network.get(0).getPeerAddress())) {
				newClientsEntry = locationEntry;
				break;
			}
		}
		assertNotNull(newClientsEntry);
	}

	/**
	 * Helper for running a process with a single {@link ContactPeersStep} step. Method waits till process
	 * successfully finishes.
	 * 
	 * @param fakedLocations
	 *            locations which the {@link ContactPeersStep} step has to handle
	 */
	private void runProcessStep(Locations fakedLocations, final boolean isMaster) {
		// initialize the process and the one and only step to test
		TestProcessContatctPeers process = new TestProcessContatctPeers(fakedLocations, network.get(0));
		process.setNextStep(new ContactPeersStep() {
			// override this to disable the triggering of the further process steps
			@Override
			protected void nextStep(Locations newLocations) {
				// store newly generated location map
				result = newLocations;

				if (isMaster)
					assertTrue(((PostLoginProcessContext) getProcess().getContext()).getIsDefinedAsMaster());
				else
					assertFalse(((PostLoginProcessContext) getProcess().getContext()).getIsDefinedAsMaster());

				// stop the process
				getProcess().setNextStep(null);
			}
		});
		TestProcessListener listener = new TestProcessListener();
		process.addListener(listener);
		process.start();

		// wait for the process to finish
		int waitingTime = (int) (H2HConstants.CONTACT_PEERS_AWAIT_MS / 1000) + maxWaitingTimeTillFail;
		H2HWaiter waiter = new H2HWaiter(waitingTime);
		do {
			waiter.tickASecond();
		} while (!listener.hasSucceeded());
	}

	@AfterClass
	public static void endTest() {
		NetworkTestUtil.shutdownNetwork(network);
		afterClass();
	}

	/**
	 * A sub-class of {@link PostLoginProcess} to simplify the context initialization.
	 * 
	 * @author Seppi
	 */
	private class TestProcessContatctPeers extends PostLoginProcess {
		public TestProcessContatctPeers(Locations locations, NetworkManager networkManager) {
			super(null, locations, networkManager, null, null);
		}
	}

	/**
	 * A message reply handler which rejects all message.
	 * 
	 * @author Seppi
	 */
	private static class DenyingMessageReplyHandler implements ObjectDataReply {
		@Override
		public Object reply(PeerAddress sender, Object request) throws Exception {
			return null;
		}
	}

}
