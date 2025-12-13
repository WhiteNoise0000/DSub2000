package github.paroj.dsub2000.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import github.paroj.dsub2000.R;
import github.paroj.dsub2000.util.Constants;
import github.paroj.dsub2000.util.CustomHttpHeaders;
import github.paroj.dsub2000.util.KeyStoreUtil;
import github.paroj.dsub2000.util.Util;

/**
 * UI editor for per-server "Custom HTTP Headers".
 *
 * <p>The list view never displays actual header values. Values are only shown inside the edit dialog, and even there
 * they are masked by default.</p>
 */
public class CustomHttpHeadersActivity extends SubsonicActivity {
	public static final String EXTRA_SERVER_INSTANCE = Constants.PREFERENCES_KEY_SERVER_INSTANCE;

	private int instance;
	private final List<CustomHttpHeaders.Header> headers = new ArrayList<>();
	private HeadersAdapter adapter;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		lastSelectedPosition = R.id.drawer_settings;
		setContentView(R.layout.custom_http_headers_activity);

		instance = getIntent().getIntExtra(EXTRA_SERVER_INSTANCE, -1);
		if (instance <= 0) {
			finish();
			return;
		}

		Toolbar toolbar = findViewById(R.id.main_toolbar);
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setTitle(R.string.settings_server_custom_headers_title);
			String serverName = Util.getServerName(this, instance);
			if (serverName != null && !serverName.trim().isEmpty()) {
				getSupportActionBar().setSubtitle(serverName);
			}
		}
		// This screen is a "sub-page" of Settings, so use a back arrow instead of the drawer indicator (hamburger).
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(false);
		}
		if (drawerToggle != null) {
			drawerToggle.setDrawerIndicatorEnabled(false);
			drawerToggle.setToolbarNavigationClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					finish();
				}
			});
			drawerToggle.syncState();
		}
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}

		RecyclerView recyclerView = findViewById(R.id.custom_headers_recycler);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		adapter = new HeadersAdapter(this, headers, new HeadersAdapter.Callbacks() {
			@Override
			public void onEdit(int position) {
				editHeader(position);
			}

			@Override
			public void onDelete(int position) {
				deleteHeader(position);
			}

			@Override
			public void onToggleEnabled(int position, boolean enabled) {
				headers.get(position).enabled = enabled;
				save();
			}
		});
		recyclerView.setAdapter(adapter);

		findViewById(R.id.custom_headers_add).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				addHeader();
			}
		});

		findViewById(R.id.custom_headers_add_cloudflare).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				addCloudflarePreset();
			}
		});

		load();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void load() {
		headers.clear();
		List<CustomHttpHeaders.Header> stored = CustomHttpHeaders.load(this, instance);
		for (CustomHttpHeaders.Header header : stored) {
			String value = header.value == null ? "" : header.value;
			if (header.valueEncrypted && Build.VERSION.SDK_INT >= 23) {
				String decrypted = KeyStoreUtil.decrypt(value);
				value = decrypted == null ? "" : decrypted;
			}
			headers.add(new CustomHttpHeaders.Header(header.enabled, header.name == null ? "" : header.name, value, false));
		}
		adapter.notifyDataSetChanged();
		updateEmptyView();
	}

	private void save() {
		List<CustomHttpHeaders.Header> toSave = new ArrayList<>(headers.size());
		for (CustomHttpHeaders.Header header : headers) {
			boolean enabled = header.enabled;
			String name = header.name == null ? "" : header.name.trim();
			String value = header.value == null ? "" : header.value;

			boolean encrypted = false;
			String storedValue = value;
			if (!value.isEmpty() && Build.VERSION.SDK_INT >= 23) {
				try {
					KeyStoreUtil.loadKeyStore();
				} catch (Exception ignored) {
				}
				String encryptedValue = KeyStoreUtil.encrypt(value);
				if (encryptedValue != null) {
					storedValue = encryptedValue;
					encrypted = true;
				}
			}

			toSave.add(new CustomHttpHeaders.Header(enabled, name, storedValue, encrypted));
		}

		CustomHttpHeaders.save(this, instance, toSave);
		updateEmptyView();
	}

	private void updateEmptyView() {
		TextView empty = findViewById(R.id.custom_headers_empty);
		if (headers.isEmpty()) {
			empty.setVisibility(View.VISIBLE);
		} else {
			empty.setVisibility(View.GONE);
		}
	}

	private void addHeader() {
		showEditDialog(-1, new CustomHttpHeaders.Header(true, "", "", false));
	}

	private void editHeader(int position) {
		showEditDialog(position, headers.get(position));
	}

	private void deleteHeader(final int position) {
		new AlertDialog.Builder(this)
				.setTitle(R.string.common_delete)
				.setMessage(R.string.settings_server_custom_headers_delete_confirm)
				.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						headers.remove(position);
						adapter.notifyItemRemoved(position);
						save();
					}
				})
				.setNegativeButton(R.string.common_cancel, null)
				.show();
	}

	private void addCloudflarePreset() {
		ensureHeaderExists("CF-Access-Client-Id");
		ensureHeaderExists("CF-Access-Client-Secret");
		save();
		adapter.notifyDataSetChanged();
	}

	private void ensureHeaderExists(String headerName) {
		for (CustomHttpHeaders.Header existing : headers) {
			if (existing.name != null && existing.name.trim().equalsIgnoreCase(headerName)) {
				return;
			}
		}
		headers.add(new CustomHttpHeaders.Header(true, headerName, "", false));
	}

	private void showEditDialog(final int position, CustomHttpHeaders.Header header) {
		final Context context = this;
		View view = LayoutInflater.from(this).inflate(R.layout.custom_http_header_edit_dialog, null);
		final CheckBox enabledView = view.findViewById(R.id.custom_header_enabled);
		final EditText nameView = view.findViewById(R.id.custom_header_name);
		final EditText valueView = view.findViewById(R.id.custom_header_value);
		final CheckBox showValue = view.findViewById(R.id.custom_header_show_value);

		enabledView.setChecked(header.enabled);
		nameView.setText(header.name == null ? "" : header.name);
		valueView.setText(header.value == null ? "" : header.value);

		valueView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		valueView.setTransformationMethod(PasswordTransformationMethod.getInstance());
		showValue.setChecked(false);
		showValue.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					valueView.setTransformationMethod(null);
				} else {
					valueView.setTransformationMethod(PasswordTransformationMethod.getInstance());
				}
				valueView.setSelection(valueView.getText().length());
			}
		});

		new AlertDialog.Builder(context)
				.setTitle(position >= 0 ? R.string.settings_server_custom_headers_edit : R.string.settings_server_custom_headers_add_title)
				.setView(view)
				.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String name = nameView.getText() == null ? "" : nameView.getText().toString().trim();
						String value = valueView.getText() == null ? "" : valueView.getText().toString();

						if (!name.isEmpty() && !CustomHttpHeaders.isValidHeaderName(name)) {
							Util.toast(context, R.string.settings_server_custom_headers_invalid_name);
							return;
						}

						CustomHttpHeaders.Header edited = new CustomHttpHeaders.Header(enabledView.isChecked(), name, value, false);
						if (position >= 0) {
							headers.set(position, edited);
							adapter.notifyItemChanged(position);
						} else {
							headers.add(edited);
							adapter.notifyItemInserted(headers.size() - 1);
						}
						save();
					}
				})
				.setNegativeButton(R.string.common_cancel, null)
				.show();
	}
}
