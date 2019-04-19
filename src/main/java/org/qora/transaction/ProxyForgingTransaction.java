package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.Forging;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.account.AccountData;
import org.qora.data.account.ProxyForgerData;
import org.qora.data.transaction.ProxyForgingTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.transform.Transformer;

public class ProxyForgingTransaction extends Transaction {

	// Properties
	private ProxyForgingTransactionData proxyForgingTransactionData;

	// Constructors

	public ProxyForgingTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.proxyForgingTransactionData = (ProxyForgingTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.emptyList();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getForger().getAddress()))
			return true;

		if (address.equals(this.getRecipient().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getForger().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public PublicKeyAccount getForger() {
		return new PublicKeyAccount(this.repository, this.proxyForgingTransactionData.getForgerPublicKey());
	}

	public Account getRecipient() {
		return new Account(this.repository, this.proxyForgingTransactionData.getRecipient());
	}

	// Processing

	private static final BigDecimal MAX_SHARE = BigDecimal.valueOf(100).setScale(2);

	@Override
	public ValidationResult isValid() throws DataException {
		// Check reward share given to recipient
		if (this.proxyForgingTransactionData.getShare().compareTo(BigDecimal.ZERO) <= 0
				|| this.proxyForgingTransactionData.getShare().compareTo(MAX_SHARE) >= 0)
			return ValidationResult.INVALID_FORGE_SHARE;

		PublicKeyAccount creator = getCreator();

		// Creator themselves needs to be allowed to forge
		if (!Forging.canForge(creator))
			return ValidationResult.NO_FORGING_PERMISSION;

		// Check proxy public key is correct length
		if (this.proxyForgingTransactionData.getProxyPublicKey().length != Transformer.PUBLIC_KEY_LENGTH)
			return ValidationResult.INVALID_PUBLIC_KEY;

		Account recipient = getRecipient();
		if (!Crypto.isValidAddress(recipient.getAddress()))
			return ValidationResult.INVALID_ADDRESS;

		// Check recipient has known public key
		AccountData recipientData = this.repository.getAccountRepository().getAccount(recipient.getAddress());
		if (recipientData == null || recipientData.getPublicKey() == null)
			return ValidationResult.PUBLIC_KEY_UNKNOWN;

		// Check fee is positive
		if (proxyForgingTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference
		if (!Arrays.equals(creator.getLastReference(), proxyForgingTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check creator has enough funds
		if (creator.getConfirmedBalance(Asset.QORA).compareTo(proxyForgingTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		PublicKeyAccount forger = getForger();

		// Grab any previous share info for orphaning purposes
		ProxyForgerData proxyForgerData = this.repository.getAccountRepository().getProxyForgeData(forger.getPublicKey(),
				proxyForgingTransactionData.getRecipient());

		if (proxyForgerData != null)
			proxyForgingTransactionData.setPreviousShare(proxyForgerData.getShare());

		// Save this transaction, with previous share info
		this.repository.getTransactionRepository().save(proxyForgingTransactionData);

		// Save proxy forging info
		proxyForgerData = new ProxyForgerData(forger.getPublicKey(), proxyForgingTransactionData.getRecipient(), proxyForgingTransactionData.getProxyPublicKey(), proxyForgingTransactionData.getShare());
		this.repository.getAccountRepository().save(proxyForgerData);

		// Update forger's balance
		forger.setConfirmedBalance(Asset.QORA, forger.getConfirmedBalance(Asset.QORA).subtract(proxyForgingTransactionData.getFee()));

		// Update forger's reference
		forger.setLastReference(proxyForgingTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert
		PublicKeyAccount forger = getForger();

		if (proxyForgingTransactionData.getPreviousShare() != null) {
			// Revert previous sharing arrangement
			ProxyForgerData proxyForgerData = new ProxyForgerData(forger.getPublicKey(), proxyForgingTransactionData.getRecipient(),
					proxyForgingTransactionData.getProxyPublicKey(), proxyForgingTransactionData.getPreviousShare());

			this.repository.getAccountRepository().save(proxyForgerData);
		} else {
			// No previous arrangement so simply delete
			this.repository.getAccountRepository().delete(forger.getPublicKey(), proxyForgingTransactionData.getRecipient());
		}

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(proxyForgingTransactionData);

		// Update forger's balance
		forger.setConfirmedBalance(Asset.QORA, forger.getConfirmedBalance(Asset.QORA).add(proxyForgingTransactionData.getFee()));

		// Update forger's reference
		forger.setLastReference(proxyForgingTransactionData.getReference());
	}

}