/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import java.lang.reflect.Field;

public class Model {

	long primaryKey = 0;

	static Field getPrimaryKeyField() {
		try {
			return Model.class.getField("primaryKey");
		} catch (NoSuchFieldException e) {
			return null;
		}
	}

}
