/**
 *  @author Shaun
 *  @date 4/2/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

public class PrimaryKey <E extends Model> {

	final Class<E> modelClass;
	final long value;

	PrimaryKey(Class<E> modelClass, long value) {
		this.modelClass = modelClass;
		this.value = value;
	}

}
