/**
 *  @author Shaun
 *  @date 7/24/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import android.database.sqlite.SQLiteStatement;

import java.lang.reflect.Field;

class StatementBinder {

	static <E extends Model> void bind(Store store, SQLiteStatement statement, int index, E model, Field field) {
		Object value;

		try {
			value = field.get(model);
		} catch (IllegalAccessException e) {
			// This will never actually happen, ModelEntity won't add a field unless it's public
			throw new RuntimeException(e);
		}

		Class type = value != null ? value.getClass() : null;

		Transformer transformer = store.getTransformer(field);
		if(transformer != null && value != null) {
			type = transformer.getTransformedValueClass();
			//noinspection unchecked
			value = transformer.getTransformedValue(value);
		}

		if(value == null) {
			statement.bindNull(index);
		} else if(type == Integer.class || type == int.class) {
			statement.bindLong(index, ((Integer)value).longValue());
		} else if(type == Long.class || type == long.class) {
			statement.bindLong(index, (Long) value);
		} else if(type == Boolean.class || type == boolean.class) {
			statement.bindLong(index, ((Boolean) value) ? 1L : 0L);
		} else if(type == Float.class || type == float.class) {
			statement.bindDouble(index, ((Float) value).doubleValue());
		} else if(type == Double.class || type == double.class) {
			statement.bindDouble(index, (Double) value);
		} else if(type == String.class) {
			statement.bindString(index, (String) value);
		} else if(type == Character.class) {
			statement.bindString(index, value.toString());
		} else if(type == byte[].class) {
			statement.bindBlob(index, (byte[]) value);
		} else if(value instanceof Model) {
			statement.bindLong(index, ((Model) value).primaryKey);
		} else if(value instanceof Enum) {
			statement.bindString(index, ((Enum)value).name());
		} else {
			throw new RuntimeException("Could not determine column type for field \"" + field.getName() + "\" of type \"" + type + "\" with value \"" + value + "\".");
		}
	}

}
