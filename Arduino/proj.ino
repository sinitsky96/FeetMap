#include <Wire.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_LSM303_U.h>
#include "BluetoothSerial.h" 

/* Assign a unique ID to this sensor at the same time */
Adafruit_LSM303_Accel_Unified accel = Adafruit_LSM303_Accel_Unified(54321);

// Check if Bluetooth is available
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

// Check Serial Port Profile
#if !defined(CONFIG_BT_SPP_ENABLED)
#error Serial Port Profile for Bluetooth is not available or not enabled. It is only available for the ESP32 chip.
#endif

BluetoothSerial SerialBT;
String device_name = "RD";
int flagBTConnected = false;
const int fsrPin1 = 13;  //green
const int fsrPin2 = 34;  //brown
const int fsrPin3 = 33;  //yellow



void displaySensorDetails(void)
{
  sensor_t sensor;
  accel.getSensor(&sensor);
  Serial.println("------------------------------------");
  Serial.print  ("Sensor:       "); Serial.println(sensor.name);
  Serial.print  ("Driver Ver:   "); Serial.println(sensor.version);
  Serial.print  ("Unique ID:    "); Serial.println(sensor.sensor_id);
  Serial.print  ("Max Value:    "); Serial.print(sensor.max_value); Serial.println(" m/s^2");
  Serial.print  ("Min Value:    "); Serial.print(sensor.min_value); Serial.println(" m/s^2");
  Serial.print  ("Resolution:   "); Serial.print(sensor.resolution); Serial.println(" m/s^2");
  Serial.println("------------------------------------");
  Serial.println("");
  delay(500);
}

void setup(void)
{
    // will pause Zero, Leonardo, etc until serial console opens
  Serial.begin(115200);
  SerialBT.begin(device_name);  //Bluetooth device name
  Serial.printf("The device with name \"%s\" is started.\nNow you can pair it with Bluetooth!\n", device_name.c_str());
  
  while (!Serial) {
    delay(10); // Wait for serial port to connect
  }
  
  Serial.println("Accelerometer Test");
  pinMode(fsrPin1, INPUT);
  pinMode(fsrPin2, INPUT);
  pinMode(fsrPin3, INPUT);
  
  // Initialize I2C communication
  Wire.begin();
  
  // Try to initialize the sensor
  if (!accel.begin()) {
    Serial.println("Failed to initialize LSM303 accelerometer!");
    Serial.println("Check your I2C address (should be 0x19) and wiring.");
    
    // Scan I2C bus for debugging
    Serial.println("\nScanning I2C bus...");
    byte error, address;
    int nDevices = 0;
    for(address = 1; address < 127; address++) {
      Wire.beginTransmission(address);
      error = Wire.endTransmission();
      if (error == 0) {
        Serial.print("I2C device found at address 0x");
        if (address < 16) Serial.print("0");
        Serial.println(address, HEX);
        nDevices++;
      }
    }
    if (nDevices == 0) {
      Serial.println("No I2C devices found!");
    }
    
    while(1); // Halt program
  }

  displaySensorDetails();
}

void loop(void)
{
  if (SerialBT.hasClient()) {
    if (flagBTConnected == false) {
      Serial.println("Bluetooth client connected!");
      flagBTConnected = true;
    }
    
    /* Get a new sensor event */
    sensors_event_t event;
    accel.getEvent(&event);

    int fsrValue1 = analogRead(fsrPin1);
    int fsrValue2 = analogRead(fsrPin2);
    int fsrValue3 = analogRead(fsrPin3);

    /* Send only acceleration data over Bluetooth */
    String dataString = 
        String(event.acceleration.x) + "," + 
        String(event.acceleration.y) + "," + 
        String(event.acceleration.z) + "," +
        String(fsrValue1) + "," + 
        String(fsrValue2) + "," + 
        String(fsrValue3) + "\n";
    SerialBT.print(dataString);




    /* Also print to Serial for debugging */
    // Serial.print(dataString);

    // Serial.print("Accel: ");
    // Serial.print(event.acceleration.x); Serial.print(", ");
    // Serial.print(event.acceleration.y); Serial.print(", ");
    Serial.print("FSR1: "); 
    Serial.print(fsrValue1); 
    Serial.println(" | ");
    Serial.print("FSR2: "); 
    Serial.print(fsrValue2); 
    Serial.println(" | ");
    Serial.print("FSR3: "); 
    Serial.print(fsrValue3); 
    Serial.println(" | ");




  
  delay(33); // Send data every 33ms
}
}
