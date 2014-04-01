/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 TV Guide, Inc. All rights reserved.
 */
package mocha.orm;

abstract public class Migration {

	abstract public void up();

	abstract public void down();

}
