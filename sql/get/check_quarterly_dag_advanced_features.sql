SELECT
    COUNT(*) AS total_snapshots,

    COUNT(*) FILTER (
        WHERE depth_valid
        ) AS valid_depth_snapshots,

    COUNT(*) FILTER (
        WHERE NOT depth_valid
        ) AS invalid_depth_snapshots,

    SUM(depth_observation_count) AS depth_observations,

    COUNT(*) FILTER (
        WHERE pr_cycle_time_observation_count > 0
        ) AS snapshots_with_pr_cycle_data,

    SUM(pr_cycle_time_observation_count)
        AS total_pr_cycle_observations,

    SUM(pr_cycle_time_negative_count)
        AS negative_pr_cycle_observations

FROM public.dag_snapshot_advanced_features;