package test;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class updates {

	private static Connection c;

	@Before
	public void connect() throws SQLException {
		c = common.getConnection();
		Statement stmt = c.createStatement();
		stmt.execute("SET DATABASE DEFAULT TABLE TYPE CACHED");
	}

	@After
	public void disconnect() {
		try {
			c.createStatement().execute("SHUTDOWN");
		} catch (SQLException e) {
			fail();
		}
	}

	public boolean databaseUpdating() throws SQLException {
		int databaseVersion = fetchDatabaseVersion();

		Statement stmt = c.createStatement();

		// Try not to add too many constraints as much of these checks will be performed during transaction validation
		// Also some constraints might be too harsh on competing unconfirmed transactions

		switch (databaseVersion) {
			case 0:
				// create from new
				stmt.execute("CREATE TABLE DatabaseInfo ( version INTEGER NOT NULL )");
				stmt.execute("INSERT INTO DatabaseInfo VALUES ( 0 )");
				stmt.execute("CREATE DOMAIN BlockSignature AS VARBINARY(128)");
				stmt.execute("CREATE DOMAIN Signature AS VARBINARY(64)");
				stmt.execute("CREATE DOMAIN QoraAddress AS VARCHAR(36)");
				stmt.execute("CREATE DOMAIN QoraAmount AS DECIMAL(19, 8)");
				stmt.execute("CREATE DOMAIN RegisteredName AS VARCHAR(400) COLLATE SQL_TEXT_UCC");
				stmt.execute("CREATE DOMAIN NameData AS VARCHAR(4000)");
				stmt.execute("CREATE DOMAIN PollName AS VARCHAR(400) COLLATE SQL_TEXT_UCC");
				stmt.execute("CREATE DOMAIN PollOption AS VARCHAR(400) COLLATE SQL_TEXT_UCC");
				stmt.execute("CREATE DOMAIN DataHash AS VARCHAR(100)");
				stmt.execute("CREATE DOMAIN AssetID AS BIGINT");
				stmt.execute("CREATE DOMAIN AssetName AS VARCHAR(400) COLLATE SQL_TEXT_UCC");
				stmt.execute("CREATE DOMAIN AssetOrderID AS VARCHAR(100)");
				stmt.execute("CREATE DOMAIN ATName AS VARCHAR(200) COLLATE SQL_TEXT_UCC");
				stmt.execute("CREATE DOMAIN ATType AS VARCHAR(200) COLLATE SQL_TEXT_UCC");
				break;

			case 1:
				// Blocks
				stmt.execute("CREATE TABLE Blocks (signature BlockSignature PRIMARY KEY, version TINYINT NOT NULL, reference BlockSignature, "
						+ "transaction_count INTEGER NOT NULL, total_fees QoraAmount NOT NULL, transactions_signature Signature NOT NULL, "
						+ "height INTEGER NOT NULL, generation TIMESTAMP NOT NULL, generation_target QoraAmount NOT NULL, "
						+ "generator QoraAddress NOT NULL, generation_signature Signature NOT NULL, AT_data VARBINARY(20000), AT_fees QoraAmount)");
				stmt.execute("CREATE INDEX BlockHeightIndex ON Blocks (height)");
				stmt.execute("CREATE INDEX BlockGeneratorIndex ON Blocks (generator)");
				break;

			case 2:
				// Generic transactions (null reference, creator and milestone_block for genesis transactions)
				stmt.execute("CREATE TABLE Transactions (signature Signature PRIMARY KEY, reference Signature, type TINYINT NOT NULL, "
						+ "creator QoraAddress, creation TIMESTAMP NOT NULL, fee QoraAmount NOT NULL, milestone_block BlockSignature)");
				stmt.execute("CREATE INDEX TransactionTypeIndex ON Transactions (type)");
				stmt.execute("CREATE INDEX TransactionCreationIndex ON Transactions (creation)");
				// Transaction-Block mapping ("signature" is unique as a transaction cannot be included in more than one block)
				stmt.execute("CREATE TABLE BlockTransactions (block BlockSignature, sequence INTEGER, signature Signature, "
						+ "PRIMARY KEY (block, sequence), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE, "
						+ "FOREIGN KEY (block) REFERENCES Blocks (signature) ON DELETE CASCADE)");
				// Unconfirmed transactions
				// Do we need this? If a transaction doesn't have a corresponding BlockTransactions record then it's unconfirmed?
				stmt.execute("CREATE TABLE UnconfirmedTransactions (signature Signature PRIMARY KEY, expiry TIMESTAMP NOT NULL)");
				stmt.execute("CREATE INDEX UnconfirmedTransactionExpiryIndex ON UnconfirmedTransactions (expiry)");
				// Transaction recipients
				stmt.execute("CREATE TABLE TransactionRecipients (signature Signature, recipient QoraAddress NOT NULL, "
						+ "FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 3:
				// Genesis Transactions
				stmt.execute("CREATE TABLE GenesisTransactions (signature Signature, recipient QoraAddress NOT NULL, "
						+ "amount QoraAmount NOT NULL, PRIMARY KEY (signature), "
						+ "FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 4:
				// Payment Transactions
				stmt.execute("CREATE TABLE PaymentTransactions (signature Signature, sender QoraAddress NOT NULL, recipient QoraAddress NOT NULL, "
						+ "amount QoraAmount NOT NULL, PRIMARY KEY (signature), "
						+ "FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 5:
				// Register Name Transactions
				stmt.execute("CREATE TABLE RegisterNameTransactions (signature Signature, registrant QoraAddress NOT NULL, name RegisteredName NOT NULL, "
						+ "owner QoraAddress NOT NULL, data NameData NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 6:
				// Update Name Transactions
				stmt.execute("CREATE TABLE UpdateNameTransactions (signature Signature, owner QoraAddress NOT NULL, name RegisteredName NOT NULL, "
						+ "new_owner QoraAddress NOT NULL, new_data NameData NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 7:
				// Sell Name Transactions
				stmt.execute("CREATE TABLE SellNameTransactions (signature Signature, owner QoraAddress NOT NULL, name RegisteredName NOT NULL, "
						+ "amount QoraAmount NOT NULL, PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 8:
				// Cancel Sell Name Transactions
				stmt.execute("CREATE TABLE CancelSellNameTransactions (signature Signature, owner QoraAddress NOT NULL, name RegisteredName NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 9:
				// Buy Name Transactions
				stmt.execute("CREATE TABLE BuyNameTransactions (signature Signature, buyer QoraAddress NOT NULL, name RegisteredName NOT NULL, "
						+ "seller QoraAddress NOT NULL, amount QoraAmount NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 10:
				// Create Poll Transactions
				stmt.execute("CREATE TABLE CreatePollTransactions (signature Signature, creator QoraAddress NOT NULL, poll PollName NOT NULL, "
						+ "description VARCHAR(4000) NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				// Poll options. NB: option is implicitly NON NULL and UNIQUE due to being part of compound primary key
				stmt.execute("CREATE TABLE CreatePollTransactionOptions (signature Signature, option PollOption, "
						+ "PRIMARY KEY (signature, option), FOREIGN KEY (signature) REFERENCES CreatePollTransactions (signature) ON DELETE CASCADE)");
				// For the future: add flag to polls to allow one or multiple votes per voter
				break;

			case 11:
				// Vote On Poll Transactions
				stmt.execute("CREATE TABLE VoteOnPollTransactions (signature Signature, voter QoraAddress NOT NULL, poll PollName NOT NULL, "
						+ "option_index INTEGER NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 12:
				// Arbitrary/Multi-payment Transaction Payments
				stmt.execute("CREATE TABLE SharedTransactionPayments (signature Signature, recipient QoraAddress NOT NULL, "
						+ "amount QoraAmount NOT NULL, asset AssetID NOT NULL, "
						+ "PRIMARY KEY (signature, recipient, asset), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 13:
				// Arbitrary Transactions
				stmt.execute("CREATE TABLE ArbitraryTransactions (signature Signature, creator QoraAddress NOT NULL, service TINYINT NOT NULL, "
						+ "data_hash DataHash NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				// NB: Actual data payload stored elsewhere
				// For the future: data payload should be encrypted, at the very least with transaction's reference as the seed for the encryption key
				break;

			case 14:
				// Issue Asset Transactions
				stmt.execute("CREATE TABLE IssueAssetTransactions (signature Signature, creator QoraAddress NOT NULL, asset_name AssetName NOT NULL, "
						+ "description VARCHAR(4000) NOT NULL, quantity BIGINT NOT NULL, is_divisible BOOLEAN NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				// For the future: maybe convert quantity from BIGINT to QoraAmount, regardless of divisibility
				break;

			case 15:
				// Transfer Asset Transactions
				stmt.execute("CREATE TABLE TransferAssetTransactions (signature Signature, sender QoraAddress NOT NULL, recipient QoraAddress NOT NULL, "
						+ "asset AssetID NOT NULL, amount QoraAmount NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 16:
				// Create Asset Order Transactions
				stmt.execute("CREATE TABLE CreateAssetOrderTransactions (signature Signature, creator QoraAddress NOT NULL, "
						+ "have_asset AssetID NOT NULL, have_amount QoraAmount NOT NULL, "
						+ "want_asset AssetID NOT NULL, want_amount QoraAmount NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 17:
				// Cancel Asset Order Transactions
				stmt.execute("CREATE TABLE CancelAssetOrderTransactions (signature Signature, creator QoraAddress NOT NULL, "
						+ "asset_order AssetOrderID NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 18:
				// Multi-payment Transactions
				stmt.execute("CREATE TABLE MultiPaymentTransactions (signature Signature, sender QoraAddress NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 19:
				// Deploy CIYAM AT Transactions
				stmt.execute("CREATE TABLE DeployATTransactions (signature Signature, creator QoraAddress NOT NULL, AT_name ATName NOT NULL, "
						+ "description VARCHAR(2000) NOT NULL, AT_type ATType NOT NULL, AT_tags VARCHAR(200) NOT NULL, "
						+ "creation_bytes VARBINARY(100000) NOT NULL, amount QoraAmount NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 20:
				// Message Transactions
				stmt.execute("CREATE TABLE MessageTransactions (signature Signature, sender QoraAddress NOT NULL, recipient QoraAddress NOT NULL, "
						+ "is_text BOOLEAN NOT NULL, is_encrypted BOOLEAN NOT NULL, amount QoraAmount NOT NULL, asset AssetID NOT NULL, data VARBINARY(4000) NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			default:
				// nothing to do
				return false;
		}

		// database was updated
		return true;
	}

	public int fetchDatabaseVersion() throws SQLException {
		int databaseVersion = 0;

		try {
			Statement stmt = c.createStatement();
			if (stmt.execute("SELECT version FROM DatabaseInfo")) {
				ResultSet rs = stmt.getResultSet();
				assertNotNull(rs);

				assertTrue(rs.next());

				databaseVersion = rs.getInt(1);
			}
		} catch (SQLException e) {
			// empty database?
		}

		return databaseVersion;
	}

	public void incrementDatabaseVersion() throws SQLException {
		Statement stmt = c.createStatement();
		assertFalse(stmt.execute("UPDATE DatabaseInfo SET version = version + 1"));
	}

	@Test
	public void testUpdates() {
		try {
			while (databaseUpdating())
				incrementDatabaseVersion();
		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

}
