package org.qortal.controller.arbitrary;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.list.ResourceListManager;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.settings.Settings;
import org.qortal.transaction.Transaction;
import org.qortal.utils.Base58;
import org.qortal.utils.FilesystemUtils;
import org.qortal.utils.NTP;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ArbitraryDataStorageManager extends Thread {

    public enum StoragePolicy {
        FOLLOWED_AND_VIEWED,
        FOLLOWED,
        VIEWED,
        ALL,
        NONE
    }

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataStorageManager.class);

    private static ArbitraryDataStorageManager instance;
    private volatile boolean isStopping = false;

    private Long storageCapacity = null;
    private long totalDirectorySize = 0L;
    private long lastDirectorySizeCheck = 0;

    private List<ArbitraryTransactionData> hostedTransactions;

    private static final long DIRECTORY_SIZE_CHECK_INTERVAL = 10 * 60 * 1000L; // 10 minutes

    /** Treat storage as full at 90% usage, to reduce risk of going over the limit.
     * This is necessary because we don't calculate total storage values before every write.
     * It also helps avoid a fetch/delete loop, as we will stop fetching before the hard limit. */
    private static final double STORAGE_FULL_THRESHOLD = 0.9; // 90%

    public ArbitraryDataStorageManager() {
    }

    public static ArbitraryDataStorageManager getInstance() {
        if (instance == null)
            instance = new ArbitraryDataStorageManager();

        return instance;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Arbitrary Data Storage Manager");
        try {
            while (!isStopping) {
                Thread.sleep(1000);

                Long now = NTP.getTime();
                if (now == null) {
                    continue;
                }

                // Check the total directory size if we haven't in a while
                if (this.shouldCalculateDirectorySize(now)) {
                    this.calculateDirectorySize(now);
                }

                Thread.sleep(59000);
            }
        } catch (InterruptedException e) {
            // Fall-through to exit thread...
        }
    }

    public void shutdown() {
        isStopping = true;
        this.interrupt();
        instance = null;
    }

    /**
     * Check if data relating to a transaction is allowed to
     * exist on this node, therefore making it a mirror for this data.
     *
     * @param arbitraryTransactionData - the transaction
     * @return boolean - whether to prefetch or not
     */
    public boolean canStoreData(ArbitraryTransactionData arbitraryTransactionData) {
        String name = arbitraryTransactionData.getName();

        // Don't store data unless it's an allowed type (public/private)
        if (!this.isDataTypeAllowed(arbitraryTransactionData)) {
            return false;
        }

        // Don't check for storage limits here, as it can cause the cleanup manager to delete existing data

        // Check if our storage policy and blacklist allows us to host data for this name
        switch (Settings.getInstance().getStoragePolicy()) {
            case FOLLOWED_AND_VIEWED:
            case ALL:
            case VIEWED:
                // If the policy includes viewed data, we can host it as long as it's not blacklisted
                return !this.isNameInBlacklist(name);

            case FOLLOWED:
                // If the policy is for followed data only, we have to be following it
                return this.isFollowingName(name);

                // For NONE or all else, we shouldn't host this data
            case NONE:
            default:
                return false;
        }
    }

    /**
     * Check if data relating to a transaction should be downloaded
     * automatically, making this node a mirror for that data.
     *
     * @param arbitraryTransactionData - the transaction
     * @return boolean - whether to prefetch or not
     */
    public boolean shouldPreFetchData(Repository repository, ArbitraryTransactionData arbitraryTransactionData) {
        String name = arbitraryTransactionData.getName();

        // Don't fetch anything more if we're (nearly) out of space
        // Make sure to keep STORAGE_FULL_THRESHOLD considerably less than 1, to
        // avoid a fetch/delete loop
        if (!this.isStorageSpaceAvailable(STORAGE_FULL_THRESHOLD)) {
            return false;
        }

        // Don't fetch anything if we're (nearly) out of space for this name
        // Again, make sure to keep STORAGE_FULL_THRESHOLD considerably less than 1, to
        // avoid a fetch/delete loop
        if (!this.isStorageSpaceAvailableForName(repository, arbitraryTransactionData.getName(), STORAGE_FULL_THRESHOLD)) {
            return false;
        }

        // Don't store data unless it's an allowed type (public/private)
        if (!this.isDataTypeAllowed(arbitraryTransactionData)) {
            return false;
        }

        // Handle transactions without names differently
        if (name == null) {
            return this.shouldPreFetchDataWithoutName();
        }

        // Never fetch data from blacklisted names, even if they are followed
        if (this.isNameInBlacklist(name)) {
            return false;
        }

        switch (Settings.getInstance().getStoragePolicy()) {
            case FOLLOWED:
            case FOLLOWED_AND_VIEWED:
                return this.isFollowingName(name);
                
            case ALL:
                return true;

            case NONE:
            case VIEWED:
            default:
                return false;
        }
    }

    /**
     * Don't call this method directly.
     * Use the wrapper method shouldPreFetchData() instead, as it contains
     * additional checks.
     *
     * @return boolean - whether the storage policy allows for unnamed data
     */
    private boolean shouldPreFetchDataWithoutName() {
        switch (Settings.getInstance().getStoragePolicy()) {
            case ALL:
                return true;

            case NONE:
            case VIEWED:
            case FOLLOWED:
            case FOLLOWED_AND_VIEWED:
            default:
                return false;
        }
    }

    private boolean isDataTypeAllowed(ArbitraryTransactionData arbitraryTransactionData) {
        byte[] secret = arbitraryTransactionData.getSecret();
        boolean hasSecret = (secret != null && secret.length == 32);

        if (!Settings.getInstance().isPrivateDataEnabled() && !hasSecret) {
            // Private data isn't enabled so we can't store data without a valid secret
            return false;
        }
        if (!Settings.getInstance().isPublicDataEnabled() && hasSecret) {
            // Public data isn't enabled so we can't store data with a secret
            return false;
        }
        return true;
    }

    public boolean isNameInBlacklist(String name) {
        return ResourceListManager.getInstance().listContains("blacklist", "names", name, false);
    }

    private boolean isFollowingName(String name) {
        return ResourceListManager.getInstance().listContains("followed", "names", name, false);
    }

    public List<String> followedNames() {
        return ResourceListManager.getInstance().getStringsInList("followed", "names");
    }

    private int followedNamesCount() {
        return ResourceListManager.getInstance().getItemCountForList("followed", "names");
    }


    // Hosted data

    public List<ArbitraryTransactionData> listAllHostedTransactions(Repository repository) {
        // Load from cache if we can, to avoid disk reads
        if (this.hostedTransactions != null) {
            return this.hostedTransactions;
        }

        List<ArbitraryTransactionData> arbitraryTransactionDataList = new ArrayList<>();

        // Find all hosted paths
        List<Path> allPaths = this.findAllHostedPaths();

        // Loop through each path and attempt to match it to a signature
        for (Path path : allPaths) {
            try {
                String[] contents = path.toFile().list();
                if (contents == null || contents.length == 0) {
                    // Ignore empty directories
                    continue;
                }

                String signature58 = path.getFileName().toString();
                byte[] signature = Base58.decode(signature58);
                TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
                if (transactionData == null || transactionData.getType() != Transaction.TransactionType.ARBITRARY) {
                    continue;
                }
                arbitraryTransactionDataList.add((ArbitraryTransactionData) transactionData);

            } catch (DataException e) {
                continue;
            }
        }

        // Update cache
        this.hostedTransactions = arbitraryTransactionDataList;

        return arbitraryTransactionDataList;
    }

    /**
     * Warning: this method will walk through the entire data directory
     * Do not call it too frequently as it could create high disk load
     * in environments with a large amount of hosted data.
     * @return a list of paths that are being hosted
     */
    public List<Path> findAllHostedPaths() {
        Path dataPath = Paths.get(Settings.getInstance().getDataPath());
        Path tempPath = Paths.get(Settings.getInstance().getTempDataPath());

        // Walk through 3 levels of the file tree and find directories that are greater than 32 characters in length
        // Also exclude the _temp and _misc paths if present
        List<Path> allPaths = new ArrayList<>();
        try {
            allPaths = Files.walk(dataPath, 3)
                    .filter(Files::isDirectory)
                    .filter(path -> !path.toAbsolutePath().toString().contains(tempPath.toAbsolutePath().toString())
                            && !path.toString().contains("_misc")
                            && path.getFileName().toString().length() > 32)
                    .collect(Collectors.toList());
        }
        catch (IOException e) {
            LOGGER.info("Unable to walk through hosted data: {}", e.getMessage());
        }

        return allPaths;
    }

    public void invalidateHostedTransactionsCache() {
        this.hostedTransactions = null;
    }


    // Size limits

    /**
     * Rate limit to reduce IO load
     */
    public boolean shouldCalculateDirectorySize(Long now) {
        if (now == null) {
            return false;
        }
        // If storage capacity is null, we need to calculate it
        if (this.storageCapacity == null) {
            return true;
        }
        // If we haven't checked for a while, we need to check it now
        if (now - lastDirectorySizeCheck > DIRECTORY_SIZE_CHECK_INTERVAL) {
            return true;
        }

        // We shouldn't check this time, as we want to reduce IO load on the SSD/HDD
        return false;
    }

    public void calculateDirectorySize(Long now) {
        if (now == null) {
            return;
        }

        long totalSize = 0;
        long remainingCapacity = 0;

        // Calculate remaining capacity
        try {
            remainingCapacity = this.getRemainingUsableStorageCapacity();
        } catch (IOException e) {
            LOGGER.info("Unable to calculate remaining storage capacity: {}", e.getMessage());
            return;
        }

        // Calculate total size of data directory
        LOGGER.trace("Calculating data directory size...");
        Path dataDirectoryPath = Paths.get(Settings.getInstance().getDataPath());
        if (dataDirectoryPath.toFile().exists()) {
            totalSize += FileUtils.sizeOfDirectory(dataDirectoryPath.toFile());
        }

        // Add total size of temp directory, if it's not already inside the data directory
        Path tempDirectoryPath = Paths.get(Settings.getInstance().getTempDataPath());
        if (tempDirectoryPath.toFile().exists()) {
            if (!FilesystemUtils.isChild(tempDirectoryPath, dataDirectoryPath)) {
                LOGGER.trace("Calculating temp directory size...");
                totalSize += FileUtils.sizeOfDirectory(dataDirectoryPath.toFile());
            }
        }

        this.totalDirectorySize = totalSize;
        this.lastDirectorySizeCheck = now;

        // It's essential that used space (this.totalDirectorySize) is included in the storage capacity
        LOGGER.trace("Calculating total storage capacity...");
        long storageCapacity = remainingCapacity + this.totalDirectorySize;

        // Make sure to limit the storage capacity if the user is overriding it in the settings
        if (Settings.getInstance().getMaxStorageCapacity() != null) {
            storageCapacity = Math.min(storageCapacity, Settings.getInstance().getMaxStorageCapacity());
        }
        this.storageCapacity = storageCapacity;

        LOGGER.info("Total used: {} bytes, Total capacity: {} bytes", this.totalDirectorySize, this.storageCapacity);
    }

    private long getRemainingUsableStorageCapacity() throws IOException {
        // Create data directory if it doesn't exist so that we can perform calculations on it
        Path dataDirectoryPath = Paths.get(Settings.getInstance().getDataPath());
        if (!dataDirectoryPath.toFile().exists()) {
            Files.createDirectories(dataDirectoryPath);
        }

        return dataDirectoryPath.toFile().getUsableSpace();
    }

    public long getTotalDirectorySize() {
        return this.totalDirectorySize;
    }

    public boolean isStorageSpaceAvailable(double threshold) {
        if (!this.isStorageCapacityCalculated()) {
            return false;
        }

        long maxStorageCapacity = (long)((double)this.storageCapacity * threshold);
        if (this.totalDirectorySize >= maxStorageCapacity) {
            return false;
        }
        return true;
    }

    public boolean isStorageSpaceAvailableForName(Repository repository, String name, double threshold) {
        if (!this.isStorageSpaceAvailable(threshold)) {
            // No storage space available at all, so no need to check this name
            return false;
        }

        if (name == null) {
            // This transaction doesn't have a name, so fall back to total space limitations
            return true;
        }

        int followedNamesCount = this.followedNamesCount();
        if (followedNamesCount == 0) {
            // Not following any names, so we have space
            return true;
        }

        long totalSizeForName = 0;
        long maxStoragePerName = this.storageCapacityPerName(threshold);

        // Fetch all hosted transactions
        List<ArbitraryTransactionData> hostedTransactions = this.listAllHostedTransactions(repository);
        for (ArbitraryTransactionData transactionData : hostedTransactions) {
            String transactionName = transactionData.getName();
            if (!Objects.equals(name, transactionName)) {
                // Transaction relates to a different name
                continue;
            }

            totalSizeForName += transactionData.getSize();
        }

        // Have we reached the limit for this name?
        if (totalSizeForName > maxStoragePerName) {
            return false;
        }

        return true;
    }

    public long storageCapacityPerName(double threshold) {
        int followedNamesCount = this.followedNamesCount();
        if (followedNamesCount == 0) {
            // Not following any names, so we have the total space available
            return this.getStorageCapacityIncludingThreshold(threshold);
        }

        double maxStorageCapacity = (double)this.storageCapacity * threshold;
        long maxStoragePerName = (long)(maxStorageCapacity / (double)followedNamesCount);

        return maxStoragePerName;
    }

    public boolean isStorageCapacityCalculated() {
        return (this.storageCapacity != null);
    }

    public Long getStorageCapacity() {
        return this.storageCapacity;
    }

    public Long getStorageCapacityIncludingThreshold(double threshold) {
        if (this.storageCapacity == null) {
            return null;
        }
        return (long)(this.storageCapacity * threshold);
    }
}
