package ydp.day01.Test;

import java.util.ArrayList;
import java.util.List;

@MyRestController
public class UserController {
    private static List<User> userList = new ArrayList<>();

    static {
        userList.add(new User(1, "Jim"));
        userList.add(new User(2, "Lily"));
    }

    @MyRequestMapping("/get")
    public String get(int id) {

        for (int i = 0; i < userList.size(); i++) {

            if (userList.get(i).getId() == id) {
                return userList.get(i).getName();
            }

        }
        return "500";
    }

    @MyRequestMapping("/getAll")
    public String getAll() {
        String names = "";
        for (int i = 0; i < userList.size(); i++) {
            names += userList.get(i).getName() + "  ";
        }

        return names;
    }

/*    public static void main(String[] args) {
        Method[] methods = UserController.class.getDeclaredMethods();
        for (Method method : methods) {
            MyRequestMapping annotation = method.getAnnotation(MyRequestMapping.class);
            if (annotation == null)
                continue;
            System.out.println(method.getName() + "," + annotation.value());

        }

    }*/
}

