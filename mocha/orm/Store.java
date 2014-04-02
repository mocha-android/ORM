/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import android.app.Application;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Store {
	private Map<Class<? extends Model>, ModelEntity<? extends Model>> entities;
	private Map<Class, Transformer> transformers;
	private Map<Class<? extends Transformer>, Transformer> transformerInstances;
	private SQLiteDatabase database;
	private String databasePath;
	private int version;

	public Store(Application application, String databaseName, int version) {
		this(application.getDatabasePath(databaseName).getPath(), version);
	}

	public Store(String databasePath, int version) {
		this.entities = new HashMap<Class<? extends Model>, ModelEntity<? extends Model>>();
		this.transformers = new HashMap<Class, Transformer>();
		this.transformerInstances = new HashMap<Class<? extends Transformer>, Transformer>();
		this.databasePath = databasePath;
		this.version = version;

		this.registerTransformer(Transformer.Date.class);
		this.registerTransformer(Transformer.Calendar.class);
	}

	public <E extends Model> void registerModel(Class<E> model) {
		if(this.database != null) {
			throw new RuntimeException("You can not register a model once the store has been used.");
		}

		if(!this.entities.containsKey(model)) {
			this.entities.put(model, new ModelEntity<E>(this, model));
		}
	}

	public void registerModels(Class<? extends Model>... models) {
		for(Class<? extends Model> model : models) {
			this.registerModel(model);
		}
	}

	public void registerTransformer(Class<? extends Transformer> transformerClass) {
		if(this.transformerInstances.containsKey(transformerClass)) {
			return;
		}

		Transformer transformer;

		try {
			transformer = transformerClass.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		this.transformers.put(transformer.getValueClass(), transformer);
		this.transformerInstances.put(transformerClass, transformer);
	}

	public <E extends Model> long count(FetchRequest<E> fetchRequest) {
		return fetchRequest.getQuery(this).count();
	}

	public <E extends Model> List<E> execute(FetchRequest<E> fetchRequest) {
		return fetchRequest.getQuery(this).execute();
	}

	public <E extends Model> void save(E model) {
		ModelEntity entity = getModelEntity(model);

		if(entity == null) {
			throw new RuntimeException("Trying to save model '" + model.getClass() + "', which hasn't been registred with this store.");
		} else {
			//noinspection unchecked
			entity.save(model);
		}
	}

	public <E extends Model> void delete(E model) {
		ModelEntity entity = getModelEntity(model);

		if(entity == null) {
			throw new RuntimeException("Trying to delete model '" + model.getClass() + "', which hasn't been registred with this store.");
		} else {
			//noinspection unchecked
			entity.delete(model);
		}
	}

	public void destroy() {

	}

	SQLiteStatement compileStatement(String sql) throws SQLException {
		return this.getDatabase().compileStatement(sql);
	}

	SQLiteDatabase getDatabase() {
		if(this.database == null) {
			this.database = SQLiteDatabase.openOrCreateDatabase(this.databasePath, null);
			this.database.setForeignKeyConstraintsEnabled(true);

			SQLiteStatement tableExistsStatement = this.compileStatement("SELECT COUNT(*) FROM \"sqlite_master\" WHERE \"type\" = ? AND \"name\" = ?");
			tableExistsStatement.bindString(1, "table");

			for(ModelEntity entity : this.entities.values()) {
				tableExistsStatement.bindString(2, entity.getTable());

				if(tableExistsStatement.simpleQueryForLong() == 0) {
					entity.create(this.database);
				}
			}
		}

		return this.database;
	}

	<E extends Model> void bind(SQLiteStatement statement, int index, E model, Field field) {
		Object value;

		try {
			value = field.get(model);
		} catch (IllegalAccessException e) {
			// This will never actually happen, ModelEntity won't add a field unless it's public
			throw new RuntimeException(e);
		}

		Class type = value.getClass();

		Transformer transformer = this.getTransformer(field);
		if(transformer != null) {
			type = transformer.getTransformedValueClass();
			//noinspection unchecked
			value = transformer.getTransformedValue(value);
		}

		if(type == Integer.class || type == int.class || type == Long.class || type == long.class || type == Boolean.class || type == boolean.class) {
			statement.bindLong(index, (Long)value);
		} else if(type == Float.class || type == float.class || type == Double.class || type == double.class) {
			statement.bindDouble(index, (Double)value);
		} else if(type == String.class) {
			statement.bindString(index, (String)value);
		} else if(type == Character.class) {
			statement.bindString(index, value.toString());
		} else if(type == byte[].class) {
			statement.bindBlob(index, (byte[])value);
		} else if(value instanceof Model) {
			statement.bindLong(index, ((Model)value).primaryKey);
		} else if(value instanceof Enum) {
			statement.bindString(index, ((Enum)value).name());
		}

		throw new RuntimeException("Could not determine column type for field " + field.getName() + " of type " + type);
	}

	ColumnType getColumnType(Field field) {
		Transformer transformer = this.getTransformer(field);

		if(transformer != null) {
			return transformer.getColumnType();
		}

		Class type = field.getType();

		if(type == Integer.class || type == int.class || type == Long.class || type == long.class || type == Boolean.class || type == boolean.class) {
			return ColumnType.INTEGER;
		} else if(type == Float.class || type == float.class || type == Double.class || type == double.class) {
			return ColumnType.REAL;
		} else if(type == String.class || type == Character.class) {
			return ColumnType.TEXT;
		} else if(type == byte[].class) {
			return ColumnType.BLOB;
		} else if(Model.class.isAssignableFrom(type)) {
			return ColumnType.INTEGER;
		} else if(type.isEnum()) {
			return ColumnType.TEXT;
		}

		throw new RuntimeException("Could not determine column type for field " + field.getName() + " of type " + type);
	}

	<E> void setField(E model, Field field, Cursor cursor, int columnIndex) throws IllegalAccessException {
		Transformer transformer = this.getTransformer(field);

		if(transformer != null) {
			// TODO
			return;
		}

		Class type = field.getClass();

		if(type == Integer.class || type == int.class) {
			field.setInt(model, cursor.getInt(columnIndex));
		} else if(type == Long.class || type == long.class) {
			field.setLong(model, cursor.getLong(columnIndex));
		} else if(type == Long.class || type == long.class || type == Boolean.class || type == boolean.class) {
			field.setBoolean(model, cursor.getInt(columnIndex) == 1);
		} else if(type == Float.class || type == float.class) {
			field.setFloat(model, cursor.getFloat(columnIndex));
		} else if(type == Double.class || type == double.class) {
			field.setDouble(model, cursor.getDouble(columnIndex));
		} else if(type == String.class) {
			field.set(model, cursor.getString(columnIndex));
		} else if(type == Character.class) {
			// TODO
		} else if(type == byte[].class) {
			field.set(model, cursor.getBlob(columnIndex));
		} else if(Model.class.isAssignableFrom(type)) {
			// TODO
		} else if(type.isEnum()) {
			field.set(model, Enum.valueOf(type, cursor.getString(columnIndex)));
		}

	}

	Transformer getTransformer(Field field) {
		if(field.isAnnotationPresent(Column.class)) {
			Column column = field.getAnnotation(Column.class);

			if(column != null) {
				Class<? extends Transformer> transformerClass = column.transformer();

				if(transformerClass != null && transformerClass != Transformer.NONE.class) {
					return this.transformerInstances.get(transformerClass);
				}
			}
		}

		return this.transformers.get(field.getType());
	}

	ModelEntity getModelEntity(Model model) {
		return this.getModelEntity(model.getClass());
	}

	ModelEntity getModelEntity(Class<? extends Model> modelClass) {
		return this.entities.get(modelClass);
	}


}
