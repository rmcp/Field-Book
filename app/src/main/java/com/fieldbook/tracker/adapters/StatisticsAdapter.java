package com.fieldbook.tracker.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.StatisticsActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class StatisticsAdapter extends RecyclerView.Adapter<StatisticsAdapter.ViewHolder> {

    DataHelper database;
    List<String> seasons;
    private SimpleDateFormat timeStamp;
    private static final String TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZZZZZ";


    public StatisticsAdapter(StatisticsActivity context, List<String> seasons){
        this.database = context.getDatabase();
        this.seasons = seasons;
        this.timeStamp = new SimpleDateFormat(TIME_FORMAT_PATTERN,
                Locale.getDefault());
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView stat1, stat2, stat3, stat4, stat5, stat6, stat7, stat8, year_text_view;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            stat1 = itemView.findViewById(R.id.stat_value_1);
            stat2 = itemView.findViewById(R.id.stat_value_2);
            stat3 = itemView.findViewById(R.id.stat_value_3);
            stat4 = itemView.findViewById(R.id.stat_value_4);
            stat5 = itemView.findViewById(R.id.stat_value_5);
            stat6 = itemView.findViewById(R.id.stat_value_6);
            stat7 = itemView.findViewById(R.id.stat_value_7);
            stat8 = itemView.findViewById(R.id.stat_value_8);
            year_text_view = itemView.findViewById(R.id.year_text_view);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View statsCardView = LayoutInflater.from(parent.getContext()).inflate(R.layout.statistics_card, parent, false);
        return new ViewHolder(statsCardView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int fields = database.getAllFieldObjects().size();
        int plots = database.getAllObservationUnits().length;
        ObservationModel[] observations = database.getAllObservationsFromAYear(seasons.get(position));

        Set<String> collectors = new HashSet<>();
        ArrayList<Date> dateObjects = new ArrayList<>();
        Map<String, Integer> dateCount = new HashMap<>();
        Map<String, Integer> observationCount = new HashMap<>();
        int imageCount = 0;

        for (ObservationModel observation : observations) {
            String collector = observation.getCollector();
            if (collector != null && !collector.trim().isEmpty()) {
                collectors.add(collector);
            }

            String time = observation.getObservation_time_stamp();
            Date dateObject = null;
            try {
                dateObject = timeStamp.parse(time);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            dateObjects.add(dateObject);

            if (observation.getObservation_variable_field_book_format().equals("photo")){
                imageCount++;
            }

            String date = new SimpleDateFormat("MM-dd-yyyy").format(dateObject);
            dateCount.put(date, dateCount.getOrDefault(date, 0)+1);

            String observationUnitId = observation.getObservation_unit_id();
            observationCount.put(observationUnitId, observationCount.getOrDefault(observationUnitId, 0)+1);

        }

        long totalInterval = 0;
        for (int i = 1; i< dateObjects.size(); i++){
            long diff = dateObjects.get(i).getTime() - dateObjects.get(i-1).getTime();
            if (diff <= TimeUnit.MINUTES.toMillis(30)){
                totalInterval += TimeUnit.MILLISECONDS.toSeconds(diff);
            }
        }
        String timeString = String.format("%02d:%02d:%02d", totalInterval / 3600, (totalInterval % 3600) / 60, totalInterval % 60);

        int maxObservationsInADay = 0;
        String dateWithMostObservations = null;
        for (Map.Entry<String, Integer> entry : dateCount.entrySet()) {
            if (entry.getValue() > maxObservationsInADay) {
                maxObservationsInADay = entry.getValue();
                dateWithMostObservations = entry.getKey();
            }
        }

        int maxObservationsOnSingleUnit = 0;
        String UnitWithMostObservations = null;
        for (Map.Entry<String, Integer> entry : observationCount.entrySet()) {
            if (entry.getValue() > maxObservationsOnSingleUnit) {
                maxObservationsOnSingleUnit = entry.getValue();
                UnitWithMostObservations = entry.getKey();
            }
        }

        holder.year_text_view.setText(seasons.get(position));
        holder.stat1.setText(String.valueOf(fields));
        holder.stat2.setText(String.valueOf(plots));
        holder.stat3.setText(String.valueOf(observations.length));
        holder.stat4.setText(timeString);
        holder.stat5.setText(String.valueOf(collectors.size()));
        holder.stat6.setText(String.valueOf(imageCount));
        holder.stat7.setText(dateWithMostObservations);
        holder.stat8.setText(UnitWithMostObservations);

    }

    @Override
    public int getItemCount() {
        return seasons.size();
    }

}
