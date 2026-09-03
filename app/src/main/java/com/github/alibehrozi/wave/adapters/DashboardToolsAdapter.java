package com.github.alibehrozi.wave.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.github.alibehrozi.wave.R;
import com.github.alibehrozi.wave.models.DashboardToolItem;

import java.util.ArrayList;
import java.util.List;

public class DashboardToolsAdapter extends RecyclerView.Adapter<DashboardToolsAdapter.ToolViewHolder> {

    public interface OnToolClickListener {
        void onToolClick(@NonNull DashboardToolItem tool);
    }

    private List<DashboardToolItem> items = new ArrayList<>();
    private final OnToolClickListener listener;
    private boolean isGridMode = false;

    public DashboardToolsAdapter(@NonNull OnToolClickListener listener) {
        this.listener = listener;
    }

    public void setItems(@NonNull List<DashboardToolItem> newItems) {
        this.items = new ArrayList<>(newItems);
        notifyDataSetChanged();
    }

    public void setGridMode(boolean gridMode) {
        if (this.isGridMode != gridMode) {
            this.isGridMode = gridMode;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ToolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dashboard_tool, parent, false);
        return new ToolViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ToolViewHolder holder, int position) {
        DashboardToolItem item = items.get(position);
        holder.bind(item, isGridMode, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ToolViewHolder extends RecyclerView.ViewHolder {

        private final CardView cardTool;
        private final FrameLayout layoutIconContainer;
        private final ImageView ivToolIcon;
        private final TextView tvToolTag;
        private final TextView tvToolName;
        private final TextView tvToolFrequency;
        private final TextView tvToolBandwidth;

        public ToolViewHolder(@NonNull View itemView) {
            super(itemView);
            cardTool = itemView.findViewById(R.id.card_tool);
            layoutIconContainer = itemView.findViewById(R.id.layout_icon_container);
            ivToolIcon = itemView.findViewById(R.id.iv_tool_icon);
            tvToolTag = itemView.findViewById(R.id.tv_tool_tag);
            tvToolName = itemView.findViewById(R.id.tv_tool_name);
            tvToolFrequency = itemView.findViewById(R.id.tv_tool_frequency);
            tvToolBandwidth = itemView.findViewById(R.id.tv_tool_bandwidth);
        }

        public void bind(
                @NonNull DashboardToolItem tool,
                boolean isGridMode,
                @NonNull OnToolClickListener listener
        ) {
            // Adjust card width for Grid vs Carousel
            ViewGroup.LayoutParams lp = cardTool.getLayoutParams();
            if (lp != null) {
                if (isGridMode) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                } else {
                    float density = itemView.getContext().getResources().getDisplayMetrics().density;
                    lp.width = (int) (134 * density);
                }
                cardTool.setLayoutParams(lp);
            }

            // Set content
            tvToolName.setText(tool.getName());
            tvToolFrequency.setText(tool.getFrequency());
            tvToolBandwidth.setText(tool.getBandwidth());
            tvToolTag.setText(tool.getTagText());

            ivToolIcon.setImageResource(tool.getIconRes());
            ivToolIcon.setColorFilter(tool.getAccentColor());

            // Tint the glowing icon background subtly with tool accent color
            int alphaColor = Color.argb(
                    38,
                    Color.red(tool.getAccentColor()),
                    Color.green(tool.getAccentColor()),
                    Color.blue(tool.getAccentColor())
            );
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.RECTANGLE);
            iconBg.setCornerRadius(dpToPx(12));
            iconBg.setColor(alphaColor);
            layoutIconContainer.setBackground(iconBg);

            // Apply category-specific styling to the tag badge
            styleTagBadge(tool);

            cardTool.setOnClickListener(v -> listener.onToolClick(tool));
        }

        private void styleTagBadge(@NonNull DashboardToolItem tool) {
            int tagBgColor;
            int tagTextColor;

            switch (tool.getCategory()) {
                case TRANSMIT:
                    tagBgColor = ContextCompat.getColor(itemView.getContext(), R.color.tag_tx_bg);
                    tagTextColor = ContextCompat.getColor(itemView.getContext(), R.color.tag_tx_text);
                    break;
                case SECURITY:
                    tagBgColor = ContextCompat.getColor(itemView.getContext(), R.color.tag_sec_bg);
                    tagTextColor = ContextCompat.getColor(itemView.getContext(), R.color.tag_sec_text);
                    break;
                case ANALYSIS:
                    tagBgColor = ContextCompat.getColor(itemView.getContext(), R.color.tag_scan_bg);
                    tagTextColor = ContextCompat.getColor(itemView.getContext(), R.color.tag_scan_text);
                    break;
                case RECEIVE:
                default:
                    tagBgColor = ContextCompat.getColor(itemView.getContext(), R.color.tag_rx_bg);
                    tagTextColor = ContextCompat.getColor(itemView.getContext(), R.color.tag_rx_text);
                    break;
            }

            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setShape(GradientDrawable.RECTANGLE);
            tagBg.setCornerRadius(dpToPx(6));
            tagBg.setColor(tagBgColor);

            tvToolTag.setBackground(tagBg);
            tvToolTag.setTextColor(tagTextColor);
        }

        private float dpToPx(float dp) {
            return dp * itemView.getContext().getResources().getDisplayMetrics().density;
        }
    }
}
