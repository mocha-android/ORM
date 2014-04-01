/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */

package mocha.orm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {

	public String name() default "";
	public boolean index() default false;
	public boolean indexAsc() default false;
	public boolean indexDesc() default false;

	public int length() default -1;

	public boolean notNull() default false;
	public boolean unique() default false;

}
