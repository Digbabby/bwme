package com.example.bwme;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.ViewHolder> {

    private List<VisitedPlace> locationList;

    public LocationAdapter(List<VisitedPlace> locationList) {
        this.locationList = locationList;
    }

    public void updateData(List<VisitedPlace> newList) {
        this.locationList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_location, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VisitedPlace currentPlace = locationList.get(position);

        holder.tvDate.setText(currentPlace.readableDate);
        holder.tvCategory.setText(currentPlace.category != null ? currentPlace.category : "No Category");

        String coords = String.format(Locale.getDefault(), "%.4f, %.4f",
                currentPlace.latitude, currentPlace.longitude);
        holder.tvCoords.setText(coords);
    }

    @Override
    public int getItemCount() {
        return locationList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvCategory, tvCoords;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvCoords = itemView.findViewById(R.id.tvCoords);
        }
    }
}
