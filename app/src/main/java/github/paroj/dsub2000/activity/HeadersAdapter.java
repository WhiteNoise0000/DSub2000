package github.paroj.dsub2000.activity;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import github.paroj.dsub2000.R;
import github.paroj.dsub2000.util.CustomHttpHeaders;

/**
 * RecyclerView adapter for custom header rows.
 *
 * <p>Important: this adapter never renders header values to the screen.</p>
 */
public class HeadersAdapter extends RecyclerView.Adapter<HeadersAdapter.ViewHolder> {
	public interface Callbacks {
		void onEdit(int position);
		void onDelete(int position);
		void onToggleEnabled(int position, boolean enabled);
	}

	private final LayoutInflater inflater;
	private final List<CustomHttpHeaders.Header> headers;
	private final Callbacks callbacks;

	public HeadersAdapter(Context context, List<CustomHttpHeaders.Header> headers, Callbacks callbacks) {
		this.inflater = LayoutInflater.from(context);
		this.headers = headers;
		this.callbacks = callbacks;
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = inflater.inflate(R.layout.custom_http_header_row, parent, false);
		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		CustomHttpHeaders.Header header = headers.get(position);
		Context context = holder.itemView.getContext();

		String name = header.name == null ? "" : header.name.trim();
		holder.name.setText(name.isEmpty() ? context.getString(R.string.settings_server_custom_headers_unnamed) : name);

		holder.value.setText(R.string.settings_server_custom_headers_value_hidden);
		bindEnabledState(holder, context, header.enabled);

		holder.enabled.setOnCheckedChangeListener(null);
		holder.enabled.setChecked(header.enabled);
		holder.enabled.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
				bindEnabledState(holder, context, isChecked);
				int adapterPosition = holder.getBindingAdapterPosition();
				if (adapterPosition != RecyclerView.NO_POSITION) {
					callbacks.onToggleEnabled(adapterPosition, isChecked);
				}
			}
		});

		holder.edit.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int adapterPosition = holder.getBindingAdapterPosition();
				if (adapterPosition != RecyclerView.NO_POSITION) {
					callbacks.onEdit(adapterPosition);
				}
			}
		});
		holder.delete.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int adapterPosition = holder.getBindingAdapterPosition();
				if (adapterPosition != RecyclerView.NO_POSITION) {
					callbacks.onDelete(adapterPosition);
				}
			}
		});

		holder.itemView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int adapterPosition = holder.getBindingAdapterPosition();
				if (adapterPosition != RecyclerView.NO_POSITION) {
					callbacks.onEdit(adapterPosition);
				}
			}
		});
	}

	@Override
	public int getItemCount() {
		return headers.size();
	}

	private void bindEnabledState(ViewHolder holder, Context context, boolean enabled) {
		int statusText = enabled ? R.string.common_enabled : R.string.common_disabled;
		holder.status.setText(statusText);
		holder.status.setBackgroundResource(enabled ? R.drawable.custom_http_header_status_enabled : R.drawable.custom_http_header_status_disabled);
		holder.status.setTextColor(enabled ? Color.WHITE : getThemeColor(context, android.R.attr.textColorSecondary));
		holder.itemView.setAlpha(enabled ? 1f : 0.86f);
	}

	private static int getThemeColor(Context context, int attr) {
		TypedArray colors = context.obtainStyledAttributes(new int[]{attr});
		try {
			return colors.getColor(0, Color.WHITE);
		} finally {
			colors.recycle();
		}
	}

	static class ViewHolder extends RecyclerView.ViewHolder {
		final TextView name;
		final TextView value;
		final TextView status;
		final SwitchCompat enabled;
		final ImageButton edit;
		final ImageButton delete;

		ViewHolder(@NonNull View itemView) {
			super(itemView);
			name = itemView.findViewById(R.id.custom_header_row_name);
			value = itemView.findViewById(R.id.custom_header_row_value);
			status = itemView.findViewById(R.id.custom_header_row_status);
			enabled = itemView.findViewById(R.id.custom_header_row_enabled);
			edit = itemView.findViewById(R.id.custom_header_row_edit);
			delete = itemView.findViewById(R.id.custom_header_row_delete);
		}
	}
}
