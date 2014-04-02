/**
 *  @author Shaun
 *  @date 4/2/14
 *  @copyright 2014 Mocha. All rights reserved.
 */

package mocha.orm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Transformable {

	public Class<? extends Transformer> value();

}
