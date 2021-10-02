package org.qortal.test;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.controller.BlockMinter;
import org.qortal.controller.Controller;
import org.qortal.data.block.BlockData;
import org.qortal.repository.*;
import org.qortal.settings.Settings;
import org.qortal.test.common.AtUtils;
import org.qortal.test.common.Common;
import org.qortal.transform.TransformationException;
import org.qortal.utils.NTP;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BootstrapTests extends Common {

    @Before
    public void beforeTest() throws DataException, IOException {
        Common.useSettingsAndDb(Common.testSettingsFilename, false);
        NTP.setFixedOffset(Settings.getInstance().getTestNtpOffset());
        this.deleteBootstraps();
    }

    @After
    public void afterTest() throws DataException, IOException {
        this.deleteBootstraps();
        this.deleteExportDirectory();
    }

    @Test
    public void testCanCreateBootstrap() throws DataException, InterruptedException, TransformationException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            this.buildDummyBlockchain(repository);

            Bootstrap bootstrap = new Bootstrap(repository);
            assertTrue(bootstrap.canCreateBootstrap());

        }
    }

    @Test
    public void testValidateBlockchain() throws DataException, InterruptedException, TransformationException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            this.buildDummyBlockchain(repository);

            Bootstrap bootstrap = new Bootstrap(repository);
            assertTrue(bootstrap.validateBlockchain());

        }
    }


    @Test
    public void testCreateAndImportBootstrap() throws DataException, InterruptedException, TransformationException, IOException {

        Path bootstrapPath = Paths.get(String.format("%s%s", Settings.getInstance().getBootstrapFilenamePrefix(), "bootstrap-archive.7z"));
        Path archivePath = Paths.get(Settings.getInstance().getRepositoryPath(), "archive", "2-900.dat");
        BlockData block1000;
        byte[] originalArchiveContents;

        try (final Repository repository = RepositoryManager.getRepository()) {
            this.buildDummyBlockchain(repository);

            // Ensure the compressed bootstrap doesn't exist
            assertFalse(Files.exists(bootstrapPath));

            Bootstrap bootstrap = new Bootstrap(repository);
            bootstrap.create();

            // Ensure the compressed bootstrap exists
            assertTrue(Files.exists(bootstrapPath));

            // Ensure the original block archive file exists
            assertTrue(Files.exists(archivePath));
            originalArchiveContents = Files.readAllBytes(archivePath);

            // Ensure block 1000 exists in the repository
            block1000 = repository.getBlockRepository().fromHeight(1000);
            assertNotNull(block1000);

            // Ensure we can retrieve block 10 from the archive
            assertNotNull(repository.getBlockArchiveRepository().fromHeight(10));

            // Now delete block 1000
            repository.getBlockRepository().delete(block1000);
            assertNull(repository.getBlockRepository().fromHeight(1000));

            // Overwrite the archive with dummy data, and verify it
            try (PrintWriter out = new PrintWriter(archivePath.toFile())) {
                out.println("testdata");
            }
            String newline = System.getProperty("line.separator");
            assertEquals("testdata", Files.readString(archivePath).replace(newline, ""));

            // Ensure we can no longer retrieve block 10 from the archive
            assertNull(repository.getBlockArchiveRepository().fromHeight(10));

            // Import the bootstrap back in
            bootstrap.importFromPath(bootstrapPath);
        }

        // We need a new connection because we have switched to a new repository
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Ensure the block archive file exists
            assertTrue(Files.exists(archivePath));

            // and that its contents match the original
            assertArrayEquals(originalArchiveContents, Files.readAllBytes(archivePath));

            // Make sure that block 1000 exists again
            BlockData newBlock1000 = repository.getBlockRepository().fromHeight(1000);
            assertNotNull(newBlock1000);

            // and ensure that the signatures match
            assertArrayEquals(block1000.getSignature(), newBlock1000.getSignature());

            // Ensure we can retrieve block 10 from the archive
            assertNotNull(repository.getBlockArchiveRepository().fromHeight(10));
        }
    }


    private void buildDummyBlockchain(Repository repository) throws DataException, InterruptedException, TransformationException, IOException {
        // Alice self share online
        List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();
        PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
        mintingAndOnlineAccounts.add(aliceSelfShare);

        // Deploy an AT so that we have AT state data
        PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");
        byte[] creationBytes = AtUtils.buildSimpleAT();
        long fundingAmount = 1_00000000L;
        AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);

        // Mint some blocks so that we are able to archive them later
        for (int i = 0; i < 1000; i++)
            BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

        // Assume 900 blocks are trimmed (this specifies the first untrimmed height)
        repository.getBlockRepository().setOnlineAccountsSignaturesTrimHeight(901);
        repository.getATRepository().setAtTrimHeight(901);

        // Check the max archive height - this should be one less than the first untrimmed height
        final int maximumArchiveHeight = BlockArchiveWriter.getMaxArchiveHeight(repository);

        // Write blocks 2-900 to the archive
        BlockArchiveWriter writer = new BlockArchiveWriter(0, maximumArchiveHeight, repository);
        writer.setShouldEnforceFileSizeTarget(false); // To avoid the need to pre-calculate file sizes
        BlockArchiveWriter.BlockArchiveWriteResult result = writer.write();

        // Increment block archive height
        repository.getBlockArchiveRepository().setBlockArchiveHeight(901);

        // Prune all the archived blocks
        repository.getBlockRepository().pruneBlocks(0, 900);
        repository.getBlockRepository().setBlockPruneHeight(901);

        // Prune the AT states for the archived blocks
        repository.getATRepository().rebuildLatestAtStates();
        repository.getATRepository().pruneAtStates(0, 900);
        repository.getATRepository().setAtPruneHeight(901);

        // Refill cache, used by Controller.getMinimumLatestBlockTimestamp() and other methods
        Controller.getInstance().refillLatestBlocksCache();

        repository.saveChanges();
    }

    private void deleteBootstraps() throws IOException {
        try {
            Path path = Paths.get(String.format("%s%s", Settings.getInstance().getBootstrapFilenamePrefix(), "bootstrap-archive.7z"));
            Files.delete(path);

        } catch (NoSuchFileException e) {
            // Nothing to delete
        }

        try {
            Path path = Paths.get(String.format("%s%s", Settings.getInstance().getBootstrapFilenamePrefix(), "bootstrap-toponly.7z"));
            Files.delete(path);

        } catch (NoSuchFileException e) {
            // Nothing to delete
        }

        try {
            Path path = Paths.get(String.format("%s%s", Settings.getInstance().getBootstrapFilenamePrefix(), "bootstrap-full.7z"));
            Files.delete(path);

        } catch (NoSuchFileException e) {
            // Nothing to delete
        }
    }

    private void deleteExportDirectory() {
        // Delete archive directory if exists
        Path archivePath = Paths.get(Settings.getInstance().getExportPath());
        try {
            FileUtils.deleteDirectory(archivePath.toFile());
        } catch (IOException e) {

        }
    }

}
