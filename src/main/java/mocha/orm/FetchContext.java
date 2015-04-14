/**
 *  @author Shaun
 *  @date 7/24/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import java.util.HashMap;
import java.util.Map;

/**
 * Used during fetches to satisfied bidirectional relationships
 */
class FetchContext {

	private final Map<String,Model> models = new HashMap<>();

	public void add(Model model) {
		this.models.put(getKey(model.getClass(), model.primaryKey), model);
	}

	public Model get(Class<Model> modelClass, long primaryKey) {
		return this.models.get(getKey(modelClass, primaryKey));
	}

	private static String getKey(Class modelClass, long primaryKey) {
		return modelClass.getSimpleName() + primaryKey;
	}

}
