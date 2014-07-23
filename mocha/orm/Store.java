/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import android.app.Application;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.*;
import mocha.foundation.Lists;
import mocha.foundation.MObject;

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
		this.entities = new HashMap<>();
		this.transformers = new HashMap<>();
		this.transformerInstances = new HashMap<>();
		this.databasePath = databasePath;
		this.version = version;

		this.registerTransformer(Transformer.Date.class);
		this.registerTransformer(Transformer.Calendar.class);
	}

	public String getDatabasePath() {
		return this.databasePath;
	}

	public void close() {
		if(this.database != null) {
			this.database.close();
			this.database = null;
		}
	}

	public <E extends Model> Store registerModel(Class<E> model) {
		if(this.database != null) {
			throw new RuntimeException("You can not register a model once the store has been used.");
		}

		if(!this.entities.containsKey(model)) {
			this.entities.put(model, new ModelEntity<>(this, model));
		}

		return this;
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
		return fetchRequest.getQuery(this).count(fetchRequest.getFetchLimit(), fetchRequest.getFetchOffset());
	}

	public <E extends Model> List<E> execute(FetchRequest<E> fetchRequest) {
		return fetchRequest.getQuery(this).execute();
	}

	public <E extends Model> E first(FetchRequest<E> fetchRequest) {
		return Lists.first(this.execute(fetchRequest));
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

	public SQLiteDatabase getDatabase() {
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

	public void beginTransaction() {
		this.getDatabase().beginTransaction();
	}

	public void setTransactionSuccessful() {
		this.getDatabase().setTransactionSuccessful();
	}

	public boolean inTransaction() {
		return this.getDatabase().inTransaction();
	}

	public void endTransaction() {
		this.getDatabase().endTransaction();
	}

	<E extends Model> void bind(SQLiteStatement statement, int index, E model, Field field) {
		Object value;

		try {
			value = field.get(model);
		} catch (IllegalAccessException e) {
			// This will never actually happen, ModelEntity won't add a field unless it's public
			throw new RuntimeException(e);
		}


		Class type = value != null ? value.getClass() : null;

		Transformer transformer = this.getTransformer(field);
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
		} else {
			throw new RuntimeException("Could not determine column type for field " + field.getName() + " of type " + type);
		}
	}

	<E> void setField(E model, Field field, Cursor cursor, int columnIndex, boolean eagerLoadHasOnes) {
		try {
			Transformer transformer = this.getTransformer(field);

			if (transformer != null) {
				Object value = null;

				switch (transformer.getColumnType()) {
					case INTEGER:
						if(Long.class.isAssignableFrom(transformer.getTransformedValueClass())) {
							//noinspection unchecked
							value = transformer.getReverseTransformedValue(cursor.getLong(columnIndex));
						} else {
							//noinspection unchecked
							value = transformer.getReverseTransformedValue(cursor.getInt(columnIndex));
						}

						break;
					case REAL:
						if(Float.class.isAssignableFrom(transformer.getTransformedValueClass())) {
							//noinspection unchecked
							value = transformer.getReverseTransformedValue(cursor.getFloat(columnIndex));
						} else {
							//noinspection unchecked
							value = transformer.getReverseTransformedValue(cursor.getDouble(columnIndex));
						}

						break;
					case TEXT:
						//noinspection unchecked
						value = transformer.getReverseTransformedValue(cursor.getString(columnIndex));
						break;
					case BLOB:
						//noinspection unchecked
						value = transformer.getReverseTransformedValue(cursor.getBlob(columnIndex));
						break;
				}

				field.set(model, value);
				return;
			}

			Class type = field.getType();

			if (type == Integer.class || type == int.class) {
				field.setInt(model, cursor.getInt(columnIndex));
			} else if (type == Long.class || type == long.class) {
				field.setLong(model, cursor.getLong(columnIndex));
			} else if (type == Long.class || type == long.class || type == Boolean.class || type == boolean.class) {
				field.setBoolean(model, cursor.getInt(columnIndex) == 1);
			} else if (type == Float.class || type == float.class) {
				field.setFloat(model, cursor.getFloat(columnIndex));
			} else if (type == Double.class || type == double.class) {
				field.setDouble(model, cursor.getDouble(columnIndex));
			} else if (type == String.class) {
				field.set(model, cursor.getString(columnIndex));
			} else if (type == Character.class) {
				// TODO
			} else if (type == byte[].class) {
				field.set(model, cursor.getBlob(columnIndex));
			} else if (Model.class.isAssignableFrom(type)) {
				if(eagerLoadHasOnes) {
					@SuppressWarnings("unchecked") FetchRequest fetchRequest = new FetchRequest(type);
					fetchRequest.setQuery((new Query()).eq("this", String.valueOf(cursor.getLong(columnIndex))));
					field.set(model, this.first(fetchRequest));
				}
			} else if (type.isEnum()) {
				field.set(model, Enum.valueOf(type, cursor.getString(columnIndex)));
			}
		} catch(IllegalAccessException e) {
			// This should never actually happen, ModelEntity ensures fields are public
		}
	}

	Transformer getTransformer(Field field) {
		if(field.isAnnotationPresent(Transformable.class)) {
			return this.transformerInstances.get(field.getAnnotation(Transformable.class).value());
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
