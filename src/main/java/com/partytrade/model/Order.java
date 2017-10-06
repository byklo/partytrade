package com.partytrade.model;

import java.math.BigDecimal;

/*
{
	"type": "MARKET" <OR> "LIMIT",
	"units": +/- dddd.dd,
	"price": dddd.dd <OR> 0
	"accountId": <account-id>
}
*/

public final class Order {
	public final String type;
	public final BigDecimal units;
	public final BigDecimal price;
	public final String accountId;

	public Order(String _type, BigDecimal _units, BigDecimal _price, String _accountId) {
		this.type = _type;
		this.units = _units;
		this.price = _price;
		this.accountId = _accountId;
	}
}