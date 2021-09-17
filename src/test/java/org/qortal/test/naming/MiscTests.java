package org.qortal.test.naming;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.controller.BlockMinter;
import org.qortal.data.transaction.*;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.*;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.ValidationResult;

public class MiscTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testGetRecentNames() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "initial-name";
			String data = "{\"age\":30}";

			RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			List<String> recentNames = repository.getNameRepository().getRecentNames(0L);

			assertNotNull(recentNames);
			assertFalse(recentNames.isEmpty());
		}
	}

	// test trying to register same name twice
	@Test
	public void testDuplicateRegisterName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "test-name";
			String data = "{\"age\":30}";

			RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// duplicate
			String duplicateName = "TEST-nÁme";
			transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), duplicateName, data);
			Transaction transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(alice);

			ValidationResult result = transaction.importAsUnconfirmed();
			assertTrue("Transaction should be invalid", ValidationResult.OK != result);
		}
	}

	// test trying to register same name twice (with different creator)
	@Test
	public void testDuplicateRegisterNameWithDifferentCreator() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "test-name";
			String data = "{}";

			RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// duplicate (this time registered by Bob)
			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			String duplicateName = "TEST-nÁme";
			transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(bob), duplicateName, data);
			Transaction transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(alice);

			ValidationResult result = transaction.importAsUnconfirmed();
			assertTrue("Transaction should be invalid", ValidationResult.OK != result);
		}
	}

	// test register then trying to update another name to existing name
	@Test
	public void testUpdateToExistingName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "test-name";
			String data = "{\"age\":30}";

			TransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Register another name that we will later attempt to rename to first name (above)
			String otherName = "new-name";
			String otherData = "";
			transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), otherName, otherData);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// we shouldn't be able to update name to existing name
			String duplicateName = "TEST-nÁme";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), otherName, duplicateName, otherData);
			Transaction transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(alice);

			ValidationResult result = transaction.importAsUnconfirmed();
			assertTrue("Transaction should be invalid", ValidationResult.OK != result);
		}
	}

	// test trying to register a name that looks like an address
	@Test
	public void testRegisterAddressAsName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = alice.getAddress();
			String data = "{\"age\":30}";

			RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
			Transaction transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(alice);

			ValidationResult result = transaction.importAsUnconfirmed();
			assertTrue("Transaction should be invalid", ValidationResult.OK != result);
		}
	}

	// test register then trying to update to a name that looks like an address
	@Test
	public void testUpdateToAddressAsName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "test-name";
			String data = "{\"age\":30}";

			TransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// we shouldn't be able to update name to an address
			String newName = alice.getAddress();
			String newData = "";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), name, newName, newData);
			Transaction transaction = Transaction.fromData(repository, transactionData);
			transaction.sign(alice);

			ValidationResult result = transaction.importAsUnconfirmed();
			assertTrue("Transaction should be invalid", ValidationResult.OK != result);
		}
	}

	// test registering and then orphaning
	@Test
	public void testRegisterNameAndOrphan() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "test-name";
			String data = "{\"age\":30}";

			// Ensure the name doesn't exist
			assertNull(repository.getNameRepository().fromName(name));

			// Register the name
			RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Ensure the name exists and the data is correct
			assertEquals(data, repository.getNameRepository().fromName(name).getData());

			// Orphan the latest block
			BlockUtils.orphanBlocks(repository, 1);

			// Ensure the name doesn't exist once again
			assertNull(repository.getNameRepository().fromName(name));
		}
	}

	@Test
	public void testOrphanAndReregisterName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "test-name";
			String data = "{\"age\":30}";

			// Ensure the name doesn't exist
			assertNull(repository.getNameRepository().fromName(name));

			// Register the name
			RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Ensure the name exists and the data is correct
			assertEquals(data, repository.getNameRepository().fromName(name).getData());

			// Orphan the latest block
			BlockUtils.orphanBlocks(repository, 1);

			// Ensure the name doesn't exist once again
			assertNull(repository.getNameRepository().fromName(name));

			// Now check there is an unconfirmed transaction
			assertEquals(1, repository.getTransactionRepository().getUnconfirmedTransactions().size());

			// Re-mint the block, including the original transaction
			BlockMinter.mintTestingBlock(repository, Common.getTestAccount(repository, "alice-reward-share"));

			// There should no longer be an unconfirmed transaction
			assertEquals(0, repository.getTransactionRepository().getUnconfirmedTransactions().size());

			// Orphan the latest block
			BlockUtils.orphanBlocks(repository, 1);

			// There should now be an unconfirmed transaction again
			assertEquals(1, repository.getTransactionRepository().getUnconfirmedTransactions().size());

			// Re-mint the block, including the original transaction
			BlockMinter.mintTestingBlock(repository, Common.getTestAccount(repository, "alice-reward-share"));

			// Ensure there are no unconfirmed transactions
			assertEquals(0, repository.getTransactionRepository().getUnconfirmedTransactions().size());
		}
	}

	// test registering and then orphaning multiple times, with a different versions of the transaction each time
	// we can sometimes end up with more than one version of a transaction, if it is signed and submitted twice
	@Test
	public void testMultipleRegisterNameAndOrphan() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "test-name";
			String data = "{\"age\":30}";

			for (int i = 1; i <= 10; i++) {

				// Ensure the name doesn't exist
				assertNull(repository.getNameRepository().fromName(name));

				// Register the name
				RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
				TransactionUtils.signAndMint(repository, transactionData, alice);

				// Ensure the name exists and the data is correct
				assertEquals(data, repository.getNameRepository().fromName(name).getData());

				// The number of unconfirmed transactions should equal the number of cycles minus 1 (because one is in a block)
				// If more than one made it into a block, this test would fail
				assertEquals(i-1, repository.getTransactionRepository().getUnconfirmedTransactions().size());

				// Orphan the latest block
				BlockUtils.orphanBlocks(repository, 1);

				// The number of unconfirmed transactions should equal the number of cycles
				assertEquals(i, repository.getTransactionRepository().getUnconfirmedTransactions().size());

				// Ensure the name doesn't exist once again
				assertNull(repository.getNameRepository().fromName(name));
			}
		}
	}

}
