/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

public class Relationship <E extends Model> {

	public static class HasOne <E> extends Relationship {

	}

	public static class HasMany <E> extends Relationship {

	}

	public static class BelongsTo <E> extends Relationship {

	}

}
