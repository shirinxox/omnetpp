package your_package_name;

import inet.networklayer.autorouting.FlatNetworkConfigurator;
import inet.physicallayer.contract.packetlevel.IRadioMedium;
import inet.physicallayer.unitdisk.UnitDiskRadioMedium;
import inet.mobility.models.pedestrian.WaypointMobility;
import inet.node.inet.Router;
import inet.node.wireless.AccessPoint;
import inet.node.wireless.WirelessHost;

network MobileNetwork {
    parameters:
        int numHosts;
        @display("bgb=580,460");
    submodules:
        configurator: FlatNetworkConfigurator {
            @display("p=100,100");
        }
        radioMedium: <default("UnitDiskRadioMedium")> like IRadioMedium {
            @display("p=100,250");
        }
        host[numHosts]: WirelessHost {
            @display("p=200,400;i=device/wifilaptop");
        }
    connections allowunconnected:
        for i=0..numHosts-1 {
            host[i].radio++ <--> radioMedium <--> configurator;
        }
}
