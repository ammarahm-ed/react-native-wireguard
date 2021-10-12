import { NativeModules } from "react-native";

const RNWireguard:{
    prepare():Promise<void>
    connect(config:string,name:string):Promise<void>
    disconnect():Promise<void>
    status(name:string):Promise<boolean>
    version():Promise<string>
} = NativeModules.RNWireguard;

export default RNWireguard;
