/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import mocha.foundation.MObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

class ModelEntity <E extends Model> extends MObject {
	private Store store;
	private Class<E> modelClass;
	private String table;

	private List<String> columns;
	private Map<Field, String> fieldToColumnMap;
	private Map<String, String> fieldNameToColumnMap;
	private Map<String, Field> columnToFieldMap;

	public static final String PRIMARY_KEY_COLUMN = "Z_ID";
	private static final Field PRIMARY_KEY_FIELD = Model.getPrimaryKeyField();

	private SQLiteStatement insertStatement;
	private SQLiteStatement updateStatement;
	private SQLiteStatement deleteStatement;

	public ModelEntity(Store store, Class<E> modelClass) {
		this.store = store;
		this.modelClass = modelClass;
		this.table = "Z" + modelClass.getSimpleName().toUpperCase();

		List<Field> fields = new ArrayList<Field>();

		Class current = modelClass;
		while(current != null && !current.equals(Model.class)) {
			Collections.addAll(fields, current.getDeclaredFields());
			current = current.getSuperclass();
		}

		int fieldSize = fields.size();

		this.columns = new ArrayList<>(fieldSize);
		this.fieldToColumnMap = new HashMap<>(fieldSize);
		this.fieldNameToColumnMap = new HashMap<>(fieldSize);
		this.columnToFieldMap = new HashMap<>(fieldSize);

		// NOTE: PRIMARY_KEY_COLUMN is intentionally not added to this.columns
		// because it's always used separately for things such as SELECT and UPDATE,
		// adding it would cause it to be queried on twice.

		this.fieldNameToColumnMap.put(PRIMARY_KEY_FIELD.getName(), PRIMARY_KEY_COLUMN);
		this.fieldToColumnMap.put(PRIMARY_KEY_FIELD, PRIMARY_KEY_COLUMN);
		this.columnToFieldMap.put(PRIMARY_KEY_COLUMN, PRIMARY_KEY_FIELD);

		for (Field field : fields) {
			if (!Modifier.isPublic(field.getModifiers())) continue;
			if (field.isAnnotationPresent(Transient.class)) continue;

			if (field.isAnnotationPresent(Transformable.class)) {
				this.store.registerTransformer(field.getAnnotation(Transformable.class).value());
			}

			String fieldName = field.getName();
			String column = "Z" + fieldName.toUpperCase();

			this.columns.add(column);
			this.fieldToColumnMap.put(field, column);
			this.fieldNameToColumnMap.put(fieldName, column);
			this.columnToFieldMap.put(column, field);
		}
	}

	public void create(SQLiteDatabase database) {
		StringBuilder builder = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
		builder.append("\"").append(this.table).append("\" (");
		builder.append("\"").append(PRIMARY_KEY_COLUMN).append("\" ").append(ColumnType.INTEGER.name()).append(" PRIMARY KEY AUTOINCREMENT");

		List<String> indexes = new ArrayList<String>();
		indexes.add(String.format("CREATE INDEX IF NOT EXISTS %s_%s_INDEX ON %s (%s)", this.table, PRIMARY_KEY_COLUMN, this.table, PRIMARY_KEY_COLUMN));

		for(Map.Entry<Field, String> fieldStringEntry : this.fieldToColumnMap.entrySet()) {
			String columnName = fieldStringEntry.getValue();
			if(columnName.equals(PRIMARY_KEY_COLUMN)) continue;

			Field field = fieldStringEntry.getKey();
			ColumnType type = this.store.getColumnType(field);

			builder.append(", ").append("\"").append(columnName).append("\" ").append(type);
			Index.Direction index = null;

			if(field.isAnnotationPresent(Length.class)) {
				builder.append("(").append(field.getAnnotation(Length.class).value()).append(")");
			}

			if(field.isAnnotationPresent(NotNull.class)) {
				builder.append(" NOT NULL");
			}

			if(field.isAnnotationPresent(Unique.class)) {
				builder.append(" UNIQUE");
			}

			if(Model.class.isAssignableFrom(field.getType())) {
				@SuppressWarnings("unchecked")
				ModelEntity entity = this.store.getModelEntity((Class<? extends Model>)field.getType());
				builder.append(" REFERENCES ").append(entity.getTable()).append("(").append(PRIMARY_KEY_FIELD).append(")");
				index = Index.Direction.NONE;
			} else if(field.isAnnotationPresent(Index.class)) {
				index = field.getAnnotation(Index.class).value();
			}

			if(index != null) {
				String direction;

				switch (index) {
					case ASC:
						direction = " ASC";
						break;
					case DESC:
						direction = " DESC";
						break;
					default:
						direction = "";
				}

				indexes.add(String.format("CREATE INDEX IF NOT EXISTS %s_%s_INDEX ON %s (%s%s)", this.table, columnName, this.table, columnName, direction));
			}
		}

		builder.append(")");

		database.execSQL(builder.toString());


		for(String index : indexes) {
			database.execSQL(index);
		}
	}

	public String getTable() {
		return this.table;
	}

	public String getColumnForFieldName(String fieldName) {
		return this.fieldNameToColumnMap.get(fieldName);
	}

	public List<String> getColumns() {
		return Collections.unmodifiableList(columns);
	}

	public void save(E model) {
		if(model.primaryKey > 0) {
			this.update(model);
		} else {
			this.insert(model);
		}
	}

	private void insert(E model) {
		if(this.insertStatement == null) {
			StringBuilder builder = new StringBuilder("INSERT INTO ");
			builder.append("\"").append(this.table).append("\" (");

			int lastColumnIndex = this.columns.size() - 1;
			String lastColumn = this.columns.get(lastColumnIndex);

			for(String column : this.columns) {
				builder.append("\"").append(column);

				if(column.equals(lastColumn)) {
					builder.append("\")");
				} else {
					builder.append("\",");
				}
			}

			builder.append(" VALUES(");

			for(int i = 0; i <= lastColumnIndex; i++) {
				if(i == lastColumnIndex) {
					builder.append("?");
				} else {
					builder.append("?,");
				}
			}

			builder.append(")");

			this.insertStatement = this.store.compileStatement(builder.toString());
		}

		if(model != null) {
			int columnIndex = 0;
			for (String column : this.columns) {
				this.store.bind(this.insertStatement, ++columnIndex, model, this.columnToFieldMap.get(column));
			}

			model.primaryKey = this.insertStatement.executeInsert();
		}
	}

	private void update(E model) {
		if(this.updateStatement == null) {
			StringBuilder builder = new StringBuilder("UPDATE ");
			builder.append("\"").append(this.table).append("\" SET ");

			int lastColumnIndex = this.columns.size() - 1;
			String lastColumn = this.columns.get(lastColumnIndex);

			for(String column : this.columns) {
				builder.append("\"").append(column).append("\" = ?");

				if(!column.equals(lastColumn)) {
					builder.append(",");
				}
			}


			builder.append("WHERE \"").append(PRIMARY_KEY_COLUMN).append("\" = ?");

			this.updateStatement = this.store.compileStatement(builder.toString());
		}

		int columnIndex = 0;

		for(String column : this.columns) {
			this.store.bind(this.updateStatement, ++columnIndex, model, this.columnToFieldMap.get(column));
		}

		this.store.bind(this.updateStatement, ++columnIndex, model, PRIMARY_KEY_FIELD);

		this.updateStatement.executeUpdateDelete();
	}

	public void delete(E model) {
		if(model.primaryKey <= 0) return;

		if(this.deleteStatement == null) {
			this.deleteStatement = this.store.compileStatement(String.format("DELETE FROM \"%s\" WHERE \"%s\" = ?", this.table, PRIMARY_KEY_FIELD));
		}

		this.deleteStatement.bindLong(1, model.primaryKey);
		this.deleteStatement.executeUpdateDelete();
	}

	public E parseCursor(Cursor cursor, List<String> selectedColumns) {
		E model;

		try {
			model = this.modelClass.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		int selectedColumnsSize = selectedColumns.size();
		for(int i = 0; i < selectedColumnsSize; i++) {
			String column = selectedColumns.get(i);
			Field field = this.columnToFieldMap.get(column);

			if(field != null) {
				this.store.setField(model, field, cursor, i);
			}
		}

		return model;
	}

}
