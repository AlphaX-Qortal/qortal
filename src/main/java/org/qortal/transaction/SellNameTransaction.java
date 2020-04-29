package org.qortal.transaction;

import java.util.Collections;
import java.util.List;

import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.SellNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.naming.Name;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import com.google.common.base.Utf8;

public class SellNameTransaction extends Transaction {

	/** Maximum amount/price for selling a name. Chosen so value, including 8 decimal places, encodes into 8 bytes or fewer. */
	private static final long MAX_AMOUNT = Asset.MAX_QUANTITY;

	// Properties
	private SellNameTransactionData sellNameTransactionData;

	// Constructors

	public SellNameTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.sellNameTransactionData = (SellNameTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.emptyList();
	}

	// Navigation

	public Account getOwner() {
		return this.getCreator();
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		String name = this.sellNameTransactionData.getName();

		// Check name size bounds
		int nameLength = Utf8.encodedLength(name);
		if (nameLength < 1 || nameLength > Name.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check name is lowercase
		if (!name.equals(name.toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		NameData nameData = this.repository.getNameRepository().fromName(name);

		// Check name exists
		if (nameData == null)
			return ValidationResult.NAME_DOES_NOT_EXIST;

		// Check name isn't currently for sale
		if (nameData.getIsForSale())
			return ValidationResult.NAME_ALREADY_FOR_SALE;

		// Check transaction's public key matches name's current owner
		Account owner = getOwner();
		if (!owner.getAddress().equals(nameData.getOwner()))
			return ValidationResult.INVALID_NAME_OWNER;

		// Check amount is positive
		if (this.sellNameTransactionData.getAmount() <= 0)
			return ValidationResult.NEGATIVE_AMOUNT;

		// Check amount within bounds
		if (this.sellNameTransactionData.getAmount() >= MAX_AMOUNT)
			return ValidationResult.INVALID_AMOUNT;

		// Check issuer has enough funds
		if (owner.getConfirmedBalance(Asset.QORT) < this.sellNameTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Sell Name
		Name name = new Name(this.repository, this.sellNameTransactionData.getName());
		name.sell(this.sellNameTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert name
		Name name = new Name(this.repository, this.sellNameTransactionData.getName());
		name.unsell(this.sellNameTransactionData);
	}

}
