/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

public enum ColumnType {

	INTEGER(Long.class),
	REAL(Float.class),
	TEXT(String.class),
	BLOB(byte[].class);

	public final Class typeClass;

	private ColumnType(Class typeClass) {
		this.typeClass = typeClass;
	}

}
