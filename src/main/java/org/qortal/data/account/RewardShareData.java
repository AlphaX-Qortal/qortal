package org.qortal.data.account;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class RewardShareData {

	// Properties
	private byte[] minterPublicKey;

	// "minter" is called "mintingAccount" instead
	@XmlTransient
	@Schema(hidden = true)
	private String minter;

	private String recipient;
	private byte[] rewardSharePublicKey;

	// JAXB to use separate getter
	@XmlTransient
	@Schema(hidden = true)
	private int sharePercent;

	// Constructors

	// For JAXB
	protected RewardShareData() {
	}

	// Used when fetching from repository
	public RewardShareData(byte[] minterPublicKey, String minter, String recipient, byte[] rewardSharePublicKey, int sharePercent) {
		this.minterPublicKey = minterPublicKey;
		this.minter = minter;
		this.recipient = recipient;
		this.rewardSharePublicKey = rewardSharePublicKey;
		this.sharePercent = sharePercent;
	}

	// Getters / setters

	public byte[] getMinterPublicKey() {
		return this.minterPublicKey;
	}

	public String getMinter() {
		return this.minter;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public byte[] getRewardSharePublicKey() {
		return this.rewardSharePublicKey;
	}

	/** Returns share percent scaled by 100. i.e. 12.34% is represented by 1234 */
	public int getSharePercent() {
		return this.sharePercent;
	}

	// Some JAXB/API-related getters

	@XmlElement(name = "mintingAccount")
	public String getMintingAccount() {
		return this.minter;
	}

	@XmlElement(name = "sharePercent")
	public BigDecimal getSharePercentJaxb() {
		return BigDecimal.valueOf(this.sharePercent, 2);
	}

}
