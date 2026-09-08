package com.github.alibehrozi.wave.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.github.alibehrozi.wave.R;
import com.github.alibehrozi.wave.walkie.VoiceChanger;

import java.util.List;

public class VoiceEffectAdapter extends RecyclerView.Adapter<VoiceEffectAdapter.ViewHolder> {

    public interface OnEffectSelectedListener {
        void onEffectSelected(VoiceChanger.EffectType effect);
    }

    private final List<VoiceChanger.EffectType> effects;
    private final OnEffectSelectedListener listener;
    private VoiceChanger.EffectType selectedEffect = VoiceChanger.EffectType.NORMAL;

    public VoiceEffectAdapter(@NonNull List<VoiceChanger.EffectType> effects,
                              @NonNull OnEffectSelectedListener listener) {
        this.effects = effects;
        this.listener = listener;
    }

    public void setSelectedEffect(VoiceChanger.EffectType effect) {
        this.selectedEffect = effect;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_voice_effect, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VoiceChanger.EffectType item = effects.get(position);
        boolean isSelected = (item == selectedEffect);

        holder.tvName.setText(item.title);
        holder.tvDesc.setText(item.subtitle);
        holder.tvChip.setText(item.dspTag);
        holder.ivIcon.setImageResource(item.iconRes);
        holder.ivIcon.setColorFilter(item.colorAccent);

        Context context = holder.itemView.getContext();
        float density = context.getResources().getDisplayMetrics().density;

        if (isSelected) {
            holder.tvTag.setText("ACTIVE");
            holder.tvTag.setTextColor(item.colorAccent);
            holder.tvTag.setBackgroundResource(R.drawable.badge_connected);
            holder.card.setCardElevation(6 * density);

            GradientDrawable borderDrawable = new GradientDrawable();
            borderDrawable.setShape(GradientDrawable.RECTANGLE);
            borderDrawable.setCornerRadius(16 * density);
            borderDrawable.setColor(ContextCompat.getColor(context, R.color.card_bg));
            borderDrawable.setStroke((int) (2 * density), item.colorAccent);
            holder.card.setBackground(borderDrawable);
        } else {
            holder.tvTag.setText("FX");
            holder.tvTag.setTextColor(0xFF57606A);
            holder.tvTag.setBackgroundResource(R.drawable.bg_status_pill);
            holder.card.setCardElevation(2 * density);

            GradientDrawable borderDrawable = new GradientDrawable();
            borderDrawable.setShape(GradientDrawable.RECTANGLE);
            borderDrawable.setCornerRadius(16 * density);
            borderDrawable.setColor(ContextCompat.getColor(context, R.color.card_bg));
            borderDrawable.setStroke((int) (1 * density), ContextCompat.getColor(context, R.color.card_stroke));
            holder.card.setBackground(borderDrawable);
        }

        holder.itemView.setOnClickListener(v -> {
            setSelectedEffect(item);
            listener.onEffectSelected(item);
        });
    }

    @Override
    public int getItemCount() {
        return effects.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CardView card;
        final ImageView ivIcon;
        final TextView tvTag;
        final TextView tvName;
        final TextView tvDesc;
        final TextView tvChip;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_voice_effect);
            ivIcon = itemView.findViewById(R.id.iv_effect_icon);
            tvTag = itemView.findViewById(R.id.tv_effect_tag);
            tvName = itemView.findViewById(R.id.tv_effect_name);
            tvDesc = itemView.findViewById(R.id.tv_effect_desc);
            tvChip = itemView.findViewById(R.id.tv_effect_chip);
        }
    }
}
