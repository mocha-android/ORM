/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import java.lang.reflect.Field;

public class Model {

	long primaryKey = 0;

	public Model() {

	}

	static Field getPrimaryKeyField() {
		try {
			return Model.class.getDeclaredField("primaryKey");
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
