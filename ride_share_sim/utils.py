import matplotlib.pyplot as plt
from ride_share_sim.agents import Driver, Rider # Assuming this import is already there
import numpy as np # For manhattan_distance

def manhattan_distance(pos1, pos2):
    """
    Calculates Manhattan distance between two points (tuples or lists of x, y coordinates).
    pos1: tuple (x1, y1)
    pos2: tuple (x2, y2)
    """
    return np.abs(pos1[0] - pos2[0]) + np.abs(pos1[1] - pos2[1])

def plot_grid(model):
    plt.figure(figsize=(8, 8)) # Increased size slightly for better visibility of lines/legend
    ax = plt.gca()
    ax.set_xlim(0, model.grid.width)
    ax.set_ylim(0, model.grid.height)

    legend_handles = {} # To avoid duplicate legend entries

    # Plot POIs first, so they are in the background
    poi_label = "POI"
    if hasattr(model.grid, 'pois') and model.grid.pois: # Check if there are any POIs
        # Plot all POIs, but only add label for the first one to avoid duplicates if not handled by legend_handles
        # However, using legend_handles dict makes this simpler
        if poi_label not in legend_handles:
            # Plot one POI to get the handle for the legend
            px, py = model.grid.pois[0]
            legend_handles[poi_label] = plt.plot(px + 0.5, py + 0.5, "r*", markersize=12, label=poi_label)[0]
            # Plot the rest without label
            for i in range(1, len(model.grid.pois)):
                 plt.plot(model.grid.pois[i][0] + 0.5, model.grid.pois[i][1] + 0.5, "r*", markersize=12)
        else: # If POI label already in legend (e.g. from a previous frame if not clearing figure)
             for (px,py) in model.grid.pois:
                  plt.plot(px + 0.5, py + 0.5, "r*", markersize=12)


    # Agent plotting logic
    for agent in model.schedule.agents:
        if agent.pos is None: # Skip agents not yet placed on grid
            continue
            
        x, y = agent.pos
        marker_x, marker_y = x + 0.5, y + 0.5 # Center of cell

        if isinstance(agent, Driver):
            color = 'blue' 
            label = 'Driver (Idle)'
            plot_kwargs = {"marker": "o", "markersize": 8}

            if agent.status == "picking_up":
                color = 'yellow'
                label = 'Driver (Picking Up)'
                if agent.target_pos:
                    plt.plot([marker_x, agent.target_pos[0] + 0.5], 
                             [marker_y, agent.target_pos[1] + 0.5], 
                             linestyle='--', color='gold', alpha=0.8, linewidth=1.5) # Changed color slightly for line
            elif agent.status == "on_trip":
                color = 'red'
                label = 'Driver (On Trip)'
                if agent.target_pos:
                    plt.plot([marker_x, agent.target_pos[0] + 0.5], 
                             [marker_y, agent.target_pos[1] + 0.5], 
                             linestyle='-', color='orangered', alpha=0.8, linewidth=1.5) # Changed color slightly for line
            
            if label not in legend_handles:
                legend_handles[label] = plt.plot(marker_x, marker_y, **plot_kwargs, color=color, label=label)[0]
            else:
                plt.plot(marker_x, marker_y, **plot_kwargs, color=color)


        elif isinstance(agent, Rider):
            color = 'green' 
            label = 'Rider (Requesting)'
            plot_kwargs = {"marker": "s", "markersize": 6}

            if agent.status == "assigned":
                color = 'cyan'
                label = 'Rider (Assigned)'
            elif agent.status == "in_transit":
                color = 'orange'
                label = 'Rider (In Transit)'
            # Riders with status "waiting_for_request" or "arrived" are not explicitly plotted with a different color
            # "arrived" riders are removed, "waiting_for_request" would use default if not handled by "requesting"
            
            if label not in legend_handles:
                legend_handles[label] = plt.plot(marker_x, marker_y, **plot_kwargs, color=color, label=label)[0]
            else:
                plt.plot(marker_x, marker_y, **plot_kwargs, color=color)

    plt.grid(True, linestyle=':', alpha=0.5)
    # Use the collected handles for the legend
    if legend_handles: # Only show legend if there's something to show
        plt.legend(handles=list(legend_handles.values()), loc='upper left', bbox_to_anchor=(1.02, 1), borderaxespad=0.)
    
    plt.title(f"Ride-Sharing Simulation - Step: {model.schedule.steps}", fontsize=14)
    plt.xlabel("X Coordinate", fontsize=12)
    plt.ylabel("Y Coordinate", fontsize=12)
    plt.tight_layout(rect=[0, 0, 0.85, 1]) # Adjust layout to make space for legend outside
    # plt.show() should be handled by main.py
