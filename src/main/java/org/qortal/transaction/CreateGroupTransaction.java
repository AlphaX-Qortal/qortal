package org.qortal.transaction;

import java.util.Collections;
import java.util.List;

import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.crypto.Crypto;
import org.qortal.data.transaction.CreateGroupTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import com.google.common.base.Utf8;

public class CreateGroupTransaction extends Transaction {

	// Properties
	private CreateGroupTransactionData createGroupTransactionData;

	// Constructors

	public CreateGroupTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.createGroupTransactionData = (CreateGroupTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.createGroupTransactionData.getOwner());
	}

	// Navigation

	public Account getOwner() {
		return this.getCreator();
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check owner address is valid
		if (!Crypto.isValidAddress(this.createGroupTransactionData.getOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check approval threshold is valid
		if (this.createGroupTransactionData.getApprovalThreshold() == null)
			return ValidationResult.INVALID_GROUP_APPROVAL_THRESHOLD;

		// Check min/max block delay values
		if (this.createGroupTransactionData.getMinimumBlockDelay() < 0)
			return ValidationResult.INVALID_GROUP_BLOCK_DELAY;

		if (this.createGroupTransactionData.getMaximumBlockDelay() < 1)
			return ValidationResult.INVALID_GROUP_BLOCK_DELAY;

		if (this.createGroupTransactionData.getMaximumBlockDelay() < this.createGroupTransactionData.getMinimumBlockDelay())
			return ValidationResult.INVALID_GROUP_BLOCK_DELAY;

		// Check group name size bounds
		int groupNameLength = Utf8.encodedLength(this.createGroupTransactionData.getGroupName());
		if (groupNameLength < 1 || groupNameLength > Group.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check description size bounds
		int descriptionLength = Utf8.encodedLength(this.createGroupTransactionData.getDescription());
		if (descriptionLength < 1 || descriptionLength > Group.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		// Check group name is lowercase
		if (!this.createGroupTransactionData.getGroupName().equals(this.createGroupTransactionData.getGroupName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		Account creator = getCreator();

		// Check creator has enough funds
		if (creator.getConfirmedBalance(Asset.QORT) < this.createGroupTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Check the group name isn't already taken
		if (this.repository.getGroupRepository().groupExists(this.createGroupTransactionData.getGroupName()))
			return ValidationResult.GROUP_ALREADY_EXISTS;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Create Group
		Group group = new Group(this.repository, this.createGroupTransactionData);
		group.create(this.createGroupTransactionData);

		// Note newly assigned group ID in our transaction record
		this.createGroupTransactionData.setGroupId(group.getGroupData().getGroupId());

		// Save this transaction with newly assigned group ID
		this.repository.getTransactionRepository().save(this.createGroupTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Uncreate group
		Group group = new Group(this.repository, this.createGroupTransactionData.getGroupId());
		group.uncreate();

		// Remove assigned group ID from transaction record
		this.createGroupTransactionData.setGroupId(null);

		// Save this transaction with removed group ID
		this.repository.getTransactionRepository().save(this.createGroupTransactionData);
	}

}
