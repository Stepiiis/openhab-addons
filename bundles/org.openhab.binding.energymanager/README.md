# EnergyManager Binding

This binding aims to automate the switching of appliances such as a heat pump and electric boiler based on PV surplus.

## Supported Things

Binding currently supports only one thing, the `signalizator` thing.

- `signalizator`: Used to define the files of the installation and makes accessible the channels which signalize the surplus output.

## Discovery
No discovery is needed.

## Thing Configuration
For the surplus `signalizator` thing to work correctly, it has to know about the states of items in your installation.
Therefore you have to specify item names of items holidng data such as your ESS SOC, ESS power, grid power, and production power. 
The automation starts only once a certain threshold value of the ESS SOC is reached.
You can specify this threshold by setting the `minStorageSoc` parameter to a numeric value between 0 and 100 
or by setting it to a name of the item which will hold the desired value.

If you are using an item to set the value of `minStorageSoc` or `maxStorageSoc`, setup a rule, which sends a command which updates it's value after openhab is started. 
Otherwise it will have no way of knowing of it's value.
This has to be done because the binding uses the Event bus to listen to events related to states of items.

### `signalizator` Thing Configuration

| Name                            | Type    | Description                                                                         | Default | Required | Advanced |
|---------------------------------|---------|-------------------------------------------------------------------------------------|---------|----------|----------|
| refreshInterval                 | integer | Length of the interval between evaluation cycles in seconds.                        | 30      | Yes      | No       |
| peakProductionPower             | integer | Peak production power of the panels in Watts.                                       |         | Yes      | No       |
| minStorageSoc                   | string  | Name of item or numerical value of the min SOC                                      |         | No       | No       |
| maxStorageSoc                   | string  | Name of item or numerical value of the max SOC                                      |         | No       | No       |
| productionPower                 | string  | Name of item containing production power data                                       |         | Yes      | No       |
| gridPower                       | string  | Name of item containing grid consumption (<0) and grid feed in (>0)power            |         | Yes      | No       | 
| storageSoc                      | string  | Name of item containing the storage SOC                                             |         | No       | No       |
| storagePower                    | string  | Name of item containing the storage power out (<0) or in (>0)                       |         | No       | No       |
| electricityPrice                | string  | Name of item containing the current electricity price                               |         | No       | Yes      |
| initialDelay                    | integer | Delay after initialization when to start the evaluation cycle in seconds.           | 60      | No       | Yes      |
| toggleOnNegativePrice           | boolean | Surplus-output channels should send ON on negative price                            | false   | No       | Yes      |
| enableInverterLimitingHeuristic | boolean | Enable a heuristic in the calculation of surplus power. Useful for limited feedin.  | false   | No       | Yes      |
| toleratedPowerDraw              | integer | Tolerated technical power draw from the ESS or grid by the inverter.                | 0       | No       | Yes      |

## Channels

The `signalizator` lets the user define channels which will be used for evaluation. 

| Channel        | Type   | Read/Write | Description                                              |
|----------------|--------|------------|----------------------------------------------------------|
| surplus-output | Switch | R          | Channel used to specify informations about an appliance. |

### Channel type `surplus-output` parameters
This channel is used by `signalizator` to specify some metadata about he appliance which is switched by it such as its priority, rated power and hysteresis related parameters

| Parameter           | Type     | Description                                                                                                                                                            | Required |
|---------------------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| priority            | integer  | Value specifying the priority of the appliance. Higher value means lower priority. Highest priority items have priority over the surplus.                              | Yes      |
| loadPower           | integer  | Rated power of the appliance                                                                                                                                           | Yes      |   
| minRuntimeMinutes   | integer  | Minimal minutes of signaling of availability once channel already sent ON.                                                                                             | No       |
| minCooldownMinutes  | integer  | Minimal value since last running ended to wait before signaling availability.                                                                                          | No       |  
| maxElectricityPrice | integer  | Signal surplus availabilty only if the value of electricity is below this value. Only used if electricity price item defined in thing parameters is correctly updated. | No       | 

## Full Example

_Provide a full usage example based on textual configuration files._
_*.things, *.items examples are mandatory as textual configuration is well used by many users._
_*.sitemap examples are optional._

### Thing Configuration

```java
Thing energymanager:signalizator:surplus [
                        refreshInterval="30",
                        peakProductionPower=10000,
                        minStorageSoc="65",
                        maxStorageSoc="95",
                        productionPower="gPowerDC",
                        gridPower="FeedInPower",
                        storageSoc="BatterySOC",
                        storagePower="BatteryPowerCharge",
                        electricityPrice="ElectricitySpotPrice", 
                        initialDelay="30",
                        toggleOnNegativePrice="false",
                        enableInverterLimitingHeuristic="true",
                        toleratedPowerDraw="200"
                        ] {
	Channels:
        Type surplus-output : house_boiler
                    [ priority="1", loadPower="2200" ]
        Type surplus-output : house_heatpump
                    [ priority="2", loadPower="2000" ]
        Type surplus-output : pool_filtration_pump
                    [ priority="3", loadPower="500", minRuntimeMinutes="2", minCooldownMinutes="2", maxElectricityPrice="80"]
}
```
### Item Configuration

```java
Switch SurplusBoilerSwitch "Boiler switch signaling enough surplus"
        { channel="energymanager:signalizator:surplus:house_boiler" }
Switch SurplusHeatpumpSwitch "Heatpump switch signaling enough surplus"
        { channel="energymanager:signalizator:surplus:house_heatpump" }
Switch SurplusPoolPumpSwitch "Pool pump switch signaling enough surplus"
        { channel="energymanager:signalizator:surplus:pool_filtration_pump" }
```