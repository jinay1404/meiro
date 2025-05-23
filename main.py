from ride_share_sim.model import RideShareModel
from ride_share_sim.utils import plot_grid
import matplotlib.pyplot as plt

# Simulation parameters
N_STEPS = 5
WIDTH = 100
HEIGHT = 100
N_DRIVERS = 5  # Example number of drivers
N_RIDERS = 10   # Example number of riders
N_POIS = 3     # Example number of POIs

if __name__ == "__main__":
    # Initialize the model
    model = RideShareModel(width=WIDTH, height=HEIGHT, n_drivers=N_DRIVERS, n_riders=N_RIDERS, n_pois=N_POIS)

    # Run the simulation
    for i in range(N_STEPS):
        print(f"Step {i + 1}")
        model.step()
        plot_grid(model)
        plt.show() # Display the plot for the current step

    print("Simulation finished.")

    # --- Simulation Statistics ---
    print("\n--- Simulation Statistics ---")
    print(f"Completed Trips: {model.completed_trips}")
    if model.completed_trips > 0:
        avg_wait_time = model.total_wait_time / model.completed_trips
        avg_trip_duration = model.total_trip_duration / model.completed_trips
        print(f"Average Rider Wait Time: {avg_wait_time:.2f} steps")
        print(f"Average Trip Duration: {avg_trip_duration:.2f} steps")
    else:
        print("No trips completed, so no average times to display.")
