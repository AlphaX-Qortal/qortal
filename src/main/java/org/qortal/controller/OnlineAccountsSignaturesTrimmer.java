package org.qortal.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.block.BlockChain;
import org.qortal.data.block.BlockData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.utils.NTP;

public class OnlineAccountsSignaturesTrimmer implements Runnable {

	private static final Logger LOGGER = LogManager.getLogger(OnlineAccountsSignaturesTrimmer.class);

	private static final long INITIAL_SLEEP_PERIOD = 5 * 60 * 1000L + 1234L; // ms

	public void run() {
		Thread.currentThread().setName("Online Accounts trimmer");

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Don't even start trimming until initial rush has ended
			Thread.sleep(INITIAL_SLEEP_PERIOD);

			while (!Controller.isStopping()) {
				repository.discardChanges();

				Thread.sleep(Settings.getInstance().getOnlineSignaturesTrimInterval());

				BlockData chainTip = Controller.getInstance().getChainTip();
				if (chainTip == null || NTP.getTime() == null)
					continue;

				// Don't even attempt if we're mid-sync as our repository requests will be delayed for ages
				if (Controller.getInstance().isSynchronizing())
					continue;

				// Trim blockchain by removing 'old' online accounts signatures
				long upperTrimmableTimestamp = NTP.getTime() - BlockChain.getInstance().getOnlineAccountSignaturesMaxLifetime();
				int upperTrimmableHeight = repository.getBlockRepository().getHeightFromTimestamp(upperTrimmableTimestamp);

				int trimStartHeight = repository.getBlockRepository().getOnlineAccountsSignaturesTrimHeight();

				int upperBatchHeight = trimStartHeight + Settings.getInstance().getOnlineSignaturesTrimBatchSize();
				int upperTrimHeight = Math.min(upperBatchHeight, upperTrimmableHeight);

				if (trimStartHeight >= upperTrimHeight)
					continue;

				int numSigsTrimmed = repository.getBlockRepository().trimOldOnlineAccountsSignatures(trimStartHeight, upperTrimHeight);
				repository.saveChanges();

				if (numSigsTrimmed > 0) {
					LOGGER.debug(() -> String.format("Trimmed %d online accounts signature%s between blocks %d and %d",
							numSigsTrimmed, (numSigsTrimmed != 1 ? "s" : ""),
							trimStartHeight, upperTrimHeight));
				} else {
					// Can we move onto next batch?
					if (upperTrimmableHeight > upperBatchHeight) {
						repository.getBlockRepository().setOnlineAccountsSignaturesTrimHeight(upperBatchHeight);
						repository.saveChanges();

						LOGGER.debug(() -> String.format("Bumping online accounts signatures trim height to %d", upperBatchHeight));
					}
				}
			}
		} catch (DataException e) {
			LOGGER.warn(String.format("Repository issue trying to trim online accounts signatures: %s", e.getMessage()));
		} catch (InterruptedException e) {
			// Time to exit
		}
	}

}
