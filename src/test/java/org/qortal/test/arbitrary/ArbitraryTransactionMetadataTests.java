package org.qortal.test.arbitrary;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.arbitrary.ArbitraryDataDigest;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.arbitrary.ArbitraryDataFile.*;
import org.qortal.arbitrary.ArbitraryDataReader;
import org.qortal.arbitrary.exception.MissingDataException;
import org.qortal.arbitrary.misc.Category;
import org.qortal.arbitrary.misc.Service;
import org.qortal.controller.arbitrary.ArbitraryDataManager;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.ArbitraryUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.RegisterNameTransaction;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ArbitraryTransactionMetadataTests extends Common {

    @Before
    public void beforeTest() throws DataException, IllegalAccessException {
        Common.useDefaultSettings();

        // Set difficulty to 1 to speed up the tests
        FieldUtils.writeField(ArbitraryDataManager.getInstance(), "powDifficulty", 1, true);
    }

    @Test
    public void testMultipleChunks() throws DataException, IOException, MissingDataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = null; // Not used for this test
            Service service = Service.ARBITRARY_DATA;
            int chunkSize = 100;
            int dataLength = 900; // Actual data length will be longer due to encryption

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));;
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = ArbitraryUtils.generateRandomDataPath(dataLength);
            ArbitraryDataFile arbitraryDataFile = ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, ArbitraryTransactionData.Method.PUT, service, alice, chunkSize);

            // Check the chunk count is correct
            assertEquals(10, arbitraryDataFile.chunkCount());

            // Now build the latest data state for this name
            ArbitraryDataReader arbitraryDataReader = new ArbitraryDataReader(name, ResourceIdType.NAME, service, identifier);
            arbitraryDataReader.loadSynchronously(true);
            Path initialLayerPath = arbitraryDataReader.getFilePath();
            ArbitraryDataDigest initialLayerDigest = new ArbitraryDataDigest(initialLayerPath);
            initialLayerDigest.compute();

            // Its directory hash should match the original directory hash
            ArbitraryDataDigest path1Digest = new ArbitraryDataDigest(path1);
            path1Digest.compute();
            assertEquals(path1Digest.getHash58(), initialLayerDigest.getHash58());
        }
    }

    @Test
    public void testDescriptiveMetadata() throws DataException, IOException, MissingDataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = null; // Not used for this test
            Service service = Service.ARBITRARY_DATA;
            int chunkSize = 100;
            int dataLength = 900; // Actual data length will be longer due to encryption

            String title = "Test title";
            String description = "Test description";
            String tags = "Test tags";
            Category category = Category.QORTAL;

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = ArbitraryUtils.generateRandomDataPath(dataLength);
            ArbitraryDataFile arbitraryDataFile = ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name,
                    identifier, ArbitraryTransactionData.Method.PUT, service, alice, chunkSize,
                    title, description, tags, category);

            // Check the chunk count is correct
            assertEquals(10, arbitraryDataFile.chunkCount());

            // Check the metadata is correct
            assertEquals(title, arbitraryDataFile.getMetadata().getTitle());
            assertEquals(description, arbitraryDataFile.getMetadata().getDescription());
            assertEquals(tags, arbitraryDataFile.getMetadata().getTags());
            assertEquals(category, arbitraryDataFile.getMetadata().getCategory());

            // Now build the latest data state for this name
            ArbitraryDataReader arbitraryDataReader = new ArbitraryDataReader(name, ResourceIdType.NAME, service, identifier);
            arbitraryDataReader.loadSynchronously(true);
            Path initialLayerPath = arbitraryDataReader.getFilePath();
            ArbitraryDataDigest initialLayerDigest = new ArbitraryDataDigest(initialLayerPath);
            initialLayerDigest.compute();

            // Its directory hash should match the original directory hash
            ArbitraryDataDigest path1Digest = new ArbitraryDataDigest(path1);
            path1Digest.compute();
            assertEquals(path1Digest.getHash58(), initialLayerDigest.getHash58());
        }
    }

    @Test
    public void testMetadataLengths() throws DataException, IOException, MissingDataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = null; // Not used for this test
            Service service = Service.ARBITRARY_DATA;
            int chunkSize = 100;
            int dataLength = 900; // Actual data length will be longer due to encryption

            String title = "title Lorem ipsum dolor sit amet, consectetur adipiscing elit. Praesent feugiat pretium";
            String description = "description Lorem ipsum dolor sit amet, consectetur adipiscing elit. Praesent feugiat pretium massa, non pulvinar mi pretium id. Ut gravida sapien vitae dui posuere tincidunt. Quisque in nibh est. Curabitur at blandit nunc, id aliquet neque. Nulla condimentum eget dolor a egestas. Vestibulum vel tincidunt ex. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Cras congue lacus in risus mattis suscipit. Quisque nisl eros, facilisis a lorem quis, vehicula bibendum.";
            String tags = "tags Lorem ipsum dolor sit amet, consectetur adipiscing elit. Praesent feugiat pretium";
            Category category = Category.CRYPTOCURRENCY;

            String expectedTitle = "title Lorem ipsum dolor sit amet, consectetur adipiscing elit. Praesent feugiat "; // 80 chars
            String expectedDescription = "description Lorem ipsum dolor sit amet, consectetur adipiscing elit. Praesent feugiat pretium massa, non pulvinar mi pretium id. Ut gravida sapien vitae dui posuere tincidunt. Quisque in nibh est. Curabitur at blandit nunc, id aliquet neque. Nulla condimentum eget dolor a egestas. Vestibulum vel tincidunt ex. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Cras congue lacus in risus mattis suscipit. Quisque nisl eros, facilisis a lorem quis, vehicula biben"; // 500 chars
            String expectedTags = "tags Lorem ipsum dolor sit amet, consectetur adipiscing elit. Praesent feugiat p"; // 80 chars

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = ArbitraryUtils.generateRandomDataPath(dataLength);
            ArbitraryDataFile arbitraryDataFile = ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name,
                    identifier, ArbitraryTransactionData.Method.PUT, service, alice, chunkSize,
                    title, description, tags, category);

            // Check the metadata is correct
            assertEquals(expectedTitle, arbitraryDataFile.getMetadata().getTitle());
            assertEquals(expectedDescription, arbitraryDataFile.getMetadata().getDescription());
            assertEquals(expectedTags, arbitraryDataFile.getMetadata().getTags());
            assertEquals(category, arbitraryDataFile.getMetadata().getCategory());
        }
    }

    @Test
    public void testExistingCategories() {
        // Matching categories should be correctly located
        assertEquals(Category.QORTAL, Category.uncategorizedValueOf("QORTAL"));
        assertEquals(Category.TECHNOLOGY, Category.uncategorizedValueOf("TECHNOLOGY"));
    }

    @Test
    public void testMissingCategory() {
        // Missing or invalid categories should fall back to UNCATEGORIZED
        assertEquals(Category.UNCATEGORIZED, Category.uncategorizedValueOf("INVALID_CATEGORY"));
        assertEquals(Category.UNCATEGORIZED, Category.uncategorizedValueOf("Qortal")); // Case-sensitive match required
    }

}
