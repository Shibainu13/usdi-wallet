# How to run Connection Module

**Update**: Currently, the module is only capable of establishing connection with the mediator. Next steps will involve establishing connection with another device through the mediator and exchange messages. 

## Store Github account and token in your Gradle directory
### Window
1. Go to C:\Users\\\<username\>\\.gradle\\ (assuming you are running the project on Windows - if not, find equivalent path on your OS)
2. Create a file called `gradle.properties` and add your credentials:
### Linux
1. Edit view all file, folder.
2. Open .gradle folder
3. Find file 'gradle.properties', if not  create and add.

```bash
gpr.user=YOUR_GITHUB_USER_NAME
gpr.key=YOUR_GITHUB_TOKEN
```

## Module Orchestration
![img.png](connection_orchestration.png)

- `ConnectionManager` is the main entity governing all different handlers. This class should be singleton.
- `ProtocolHandler` is the unified interface for connection protocols. New protocol must implement this class.
- `ConnectionState` acts as an enum for connection states - unified different protocols into 4 states: `Idle`, `Success`, `Error` and `Pending`.

## Run Connection Module Demo
This demo focuses on setting up DIDComm v2 using the SDKs provided by Hyperledger Identus. The SDKs used are:
- [Hyperledger-Identus/sdk-kmp](https://github.com/hyperledger-identus/sdk-kmp)
- [Hyperledger-Identus/mediator](https://github.com/hyperledger-identus/mediator)

### Run Hyperledger-Identus/mediator

1. In a separate terminal, clone the mediator repository and open it in your IDE.
```shell
$ git clone https://github.com/hyperledger-identus/mediator.git
```

2. Update `SERVICE_ENDPOINTS` variable in `docker-compose.yml` so that your app can find the mediator instance. This variable is used to generate connection invitation regardless of where the mediator is hosted on, thus changing this only affect the invitation generated, not the endpoints of the service itself.
```yaml
services:
  mongo:
    ...

  identus-mediator:
    environment:
      ...
      # Original
      # - SERVICE_ENDPOINTS=${SERVICE_ENDPOINTS:-http://localhost:8080;ws://localhost:8080/ws}
      # This demo uses an Pixel 8 Emulator, thus in order for it to "see" the mediator endpoints hosted on the same machine, we replace `localhost` with `10.0.2.2`
      - SERVICE_ENDPOINTS=${SERVICE_ENDPOINTS:-http://10.0.2.2:8080;ws://10.0.2.2:8080/ws}
      ...
```

### Run the application

Please use an Android development IDE (Intellij IDEA, Android Studio, etc.) to run the app on an emulator for now. Other methods will be updated later.